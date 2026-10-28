package co.xenastudios.mostwanted.backend.api;

import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.support.Applications;
import co.xenastudios.mostwanted.backend.support.Forms;
import co.xenastudios.mostwanted.backend.support.Inbox;
import co.xenastudios.mostwanted.backend.support.Staff;
import co.xenastudios.mostwanted.backend.support.Tickets;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Support tickets, applications and notifications (docs/08-website.md#accounts-and-the-api).
 * Everything needs a signed-in account; applications need its Minecraft account linked
 * too. Staff (a helper rank or up in game) see every ticket; moderators and up decide
 * applications.
 */
final class SupportRoutes {

    /** New tickets per account per hour: enough for anyone, too few to spam. */
    static final int TICKETS_PER_HOUR = 3;
    private static final Set<String> TICKET_STATES = Set.of("open", "answered", "closed");
    private static final Set<String> APPLICATION_STATES = Set.of("pending", "accepted", "denied");
    private static final Map<String, String> KINDS = Map.of("guard", "guard", "staff", "staff", "creator", "creator");

    private final AccountRoutes auth;
    private final Accounts accounts;
    private final Tickets tickets;
    private final Applications applications;
    private final Inbox inbox;
    private final Staff staff;

    SupportRoutes(AccountRoutes auth, Accounts accounts, Tickets tickets, Applications applications, Inbox inbox, Staff staff) {
        this.auth = auth;
        this.accounts = accounts;
        this.tickets = tickets;
        this.applications = applications;
        this.inbox = inbox;
        this.staff = staff;
    }

    /** Who's asking: their account, its staff rank (or null), and the name they go by. */
    private record Who(Accounts.Account account, Staff.Level level) {

        String name() {
            return account.minecraftName() != null ? account.minecraftName()
                    : account.discordName() != null ? account.discordName() : "?";
        }
    }

    void register(JavalinConfig javalin) {
        javalin.routes.get("/v1/forms", ctx -> {
            Map<String, Object> forms = new LinkedHashMap<>();
            forms.put("categories", Forms.CATEGORIES);
            forms.put("subjectMax", Forms.SUBJECT_MAX);
            forms.put("bodyMax", Forms.BODY_MAX);
            forms.put("applications", Forms.APPLICATIONS);
            ctx.header("Cache-Control", "public, max-age=300").json(forms);
        });

        // ---------------------------------------------------------------- tickets

        javalin.routes.get("/v1/me/tickets", ctx -> ctx.json(tickets.mine(who(ctx).account().id())));

        javalin.routes.post("/v1/me/tickets", ctx -> {
            Who who = who(ctx);
            Map<?, ?> body = body(ctx);
            String category = text(body, "category", 16);
            if (!Forms.CATEGORIES.containsKey(category)) {
                throw new Api.Failure(HttpStatus.BAD_REQUEST, "Pick what it's about.");
            }
            String subject = required(body, "subject", "a subject", Forms.SUBJECT_MAX);
            String message = required(body, "body", "what happened", Forms.BODY_MAX);
            if (tickets.openedSince(who.account().id(), Duration.ofHours(1)) >= TICKETS_PER_HOUR) {
                throw new Api.Failure(HttpStatus.TOO_MANY_REQUESTS,
                        "You've opened " + TICKETS_PER_HOUR + " tickets in the last hour. Reply on one of those instead.");
            }
            long id = tickets.open(who.account().id(), who.name(), category, subject, message);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", id));
        });

        javalin.routes.get("/v1/tickets/{id}", ctx -> {
            Who who = who(ctx);
            Tickets.Ticket ticket = ticket(ctx, who);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("ticket", ticket);
            view.put("messages", tickets.messages(ticket.id()));
            view.put("mine", ticket.accountId() == who.account().id());
            ctx.json(view);
        });

        javalin.routes.post("/v1/tickets/{id}/messages", ctx -> {
            Who who = who(ctx);
            Tickets.Ticket ticket = ticket(ctx, who);
            if (ticket.status().equals("closed")) {
                throw new Api.Failure(HttpStatus.CONFLICT, "This ticket is closed. Open a new one if you need to.");
            }
            String message = required(body(ctx), "body", "a reply", Forms.BODY_MAX);
            boolean mine = ticket.accountId() == who.account().id();
            // Staff answering their own ticket are answering as the player.
            boolean asStaff = !mine && who.level() != null;
            tickets.reply(ticket.id(), who.account().id(), who.name(), asStaff, message);
            if (asStaff) {
                inbox.tell(ticket.accountId(), "Staff replied to your ticket \"" + ticket.subject() + "\".",
                        "/ticket?id=" + ticket.id());
            }
            ctx.status(HttpStatus.CREATED).json(Map.of("ok", true));
        });

        javalin.routes.post("/v1/tickets/{id}/close", ctx -> {
            Who who = who(ctx);
            Tickets.Ticket ticket = ticket(ctx, who);
            if (!ticket.status().equals("closed")) {
                tickets.close(ticket.id());
                if (ticket.accountId() != who.account().id()) {
                    inbox.tell(ticket.accountId(), "Your ticket \"" + ticket.subject() + "\" was closed.",
                            "/ticket?id=" + ticket.id());
                }
            }
            ctx.json(Map.of("ok", true));
        });

        // ---------------------------------------------------------------- applications

        javalin.routes.get("/v1/me/applications", ctx -> ctx.json(applications.mine(who(ctx).account().id())));

        javalin.routes.post("/v1/me/applications", ctx -> {
            Who who = who(ctx);
            if (who.account().minecraft() == null) {
                throw new Api.Failure(HttpStatus.BAD_REQUEST,
                        "Link your Minecraft account first, on your account page, so we know who's applying.");
            }
            Map<?, ?> body = body(ctx);
            String kind = text(body, "kind", 16);
            StringBuilder problem = new StringBuilder();
            Map<String, String> answers = Forms.check(kind, body.get("answers") instanceof Map<?, ?> m ? m : null, problem);
            if (answers == null) {
                throw new Api.Failure(HttpStatus.BAD_REQUEST, problem.toString());
            }
            switch (applications.send(who.account().id(), kind, answers)) {
                case SENT -> ctx.status(HttpStatus.CREATED).json(Map.of("ok", true));
                case ALREADY_WAITING -> throw new Api.Failure(HttpStatus.CONFLICT,
                        "You've already applied. Staff will get back to you: watch your account page.");
                case TOO_SOON -> throw new Api.Failure(HttpStatus.CONFLICT,
                        "You can apply again " + Applications.RETRY_AFTER.toDays() + " days after your last one was turned down.");
            }
        });

        // ---------------------------------------------------------------- notifications

        javalin.routes.get("/v1/me/notifications", ctx -> ctx.json(inbox.recent(who(ctx).account().id())));

        javalin.routes.post("/v1/me/notifications/read", ctx -> {
            inbox.readAll(who(ctx).account().id());
            ctx.json(Map.of("ok", true));
        });

        // ---------------------------------------------------------------- staff

        javalin.routes.get("/v1/staff/tickets", ctx -> {
            staff(ctx);
            ctx.json(tickets.queue(state(ctx, TICKET_STATES, "open")));
        });

        javalin.routes.get("/v1/staff/applications", ctx -> {
            staff(ctx);
            ctx.json(applications.queue(state(ctx, APPLICATION_STATES, "pending")));
        });

        javalin.routes.get("/v1/staff/applications/{id}", ctx -> {
            staff(ctx);
            ctx.json(applications.get(id(ctx)).orElseThrow(() -> new Api.Failure(HttpStatus.NOT_FOUND, "There's no such application.")));
        });

        javalin.routes.post("/v1/staff/applications/{id}", ctx -> {
            Who who = staff(ctx);
            if (!who.level().decides()) {
                throw new Api.Failure(HttpStatus.FORBIDDEN, "Moderators and up decide applications.");
            }
            Map<?, ?> body = body(ctx);
            String decision = text(body, "decision", 8);
            if (!decision.equals("accept") && !decision.equals("deny")) {
                throw new Api.Failure(HttpStatus.BAD_REQUEST, "Accept or deny.");
            }
            String note = text(body, "note", 1000);
            boolean accepted = decision.equals("accept");
            Applications.Application decided = applications.decide(id(ctx), accepted, note.isEmpty() ? null : note, who.name())
                    .orElseThrow(() -> new Api.Failure(HttpStatus.CONFLICT, "Someone already decided this one."));
            inbox.tell(decided.accountId(), decision(decided), "/account#applications");
            ctx.json(decided);
        });

        javalin.routes.after("/v1/me/*", ctx -> ctx.header("Cache-Control", "no-store"));
        javalin.routes.after("/v1/tickets/*", ctx -> ctx.header("Cache-Control", "no-store"));
        javalin.routes.after("/v1/staff/*", ctx -> ctx.header("Cache-Control", "no-store"));
    }

    /** What the player is told about a decision. */
    static String decision(Applications.Application application) {
        String what = KINDS.get(application.kind());
        if (application.status().equals("accepted")) {
            return application.kind().equals("guard")
                    ? "Your guard application was accepted! You're a guard: use /duty in game to go on duty."
                    : "Your " + what + " application was accepted! Staff will be in touch about what's next.";
        }
        return "Your " + what + " application wasn't accepted this time. See why on your account page.";
    }

    /** The staff rank to show on /v1/me: null for players. */
    String level(long accountId) {
        Staff.Level level = accounts.get(accountId).map(a -> staff.level(a.minecraft())).orElse(null);
        return level == null ? null : level.key();
    }

    int unread(long accountId) {
        return inbox.unread(accountId);
    }

    private Who who(Context ctx) {
        long id = auth.signedIn(ctx).orElseThrow(() -> new Api.Failure(HttpStatus.UNAUTHORIZED, "You're not signed in."));
        Accounts.Account account = accounts.get(id)
                .orElseThrow(() -> new Api.Failure(HttpStatus.UNAUTHORIZED, "You're not signed in."));
        return new Who(account, staff.level(account.minecraft()));
    }

    private Who staff(Context ctx) {
        Who who = who(ctx);
        if (who.level() == null) {
            throw new Api.Failure(HttpStatus.FORBIDDEN, "That's for staff.");
        }
        return who;
    }

    /** The ticket in the path, if they may see it: theirs, or they're staff. Others get "not found". */
    private Tickets.Ticket ticket(Context ctx, Who who) {
        return tickets.get(id(ctx))
                .filter(t -> t.accountId() == who.account().id() || who.level() != null)
                .orElseThrow(() -> new Api.Failure(HttpStatus.NOT_FOUND, "There's no such ticket."));
    }

    private static long id(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new Api.Failure(HttpStatus.NOT_FOUND, "Not found.");
        }
    }

    private static String state(Context ctx, Set<String> allowed, String fallback) {
        String status = ctx.queryParam("status");
        if (status == null) {
            return fallback;
        }
        if (!allowed.contains(status)) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "The status is one of " + String.join(", ", List.copyOf(allowed)) + ".");
        }
        return status;
    }

    private static Map<?, ?> body(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            if (body == null) {
                throw new IllegalArgumentException();
            }
            return body;
        } catch (RuntimeException e) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "Send the form as JSON.");
        }
    }

    private static String text(Map<?, ?> body, String name, int max) {
        Object value = body.get(name);
        String text = value instanceof String s ? s.strip() : "";
        if (text.length() > max) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "That's too long: " + max + " characters at most.");
        }
        return text;
    }

    private static String required(Map<?, ?> body, String name, String what, int max) {
        String text = text(body, name, max);
        if (text.isEmpty()) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "Write " + what + ".");
        }
        return text;
    }
}
