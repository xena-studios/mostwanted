-- Which cosmetic each player uses in each slot: their trail, escape effect, arrest
-- effect, a skin per gun, chat colour and (Legend) join message. Owning one is a
-- permission (mostwanted.cosmetic.<id>, from the store); this is only the choice.
CREATE TABLE mostwanted.cosmetic_choices (
    uuid   UUID        NOT NULL REFERENCES mostwanted.players ON DELETE CASCADE,
    -- "trail", "escape", "arrest", "skin:pistol", "chat", "join-message".
    slot   VARCHAR(32) NOT NULL,
    -- A cosmetic id, a colour, or the join message's text.
    choice VARCHAR(64) NOT NULL,
    PRIMARY KEY (uuid, slot)
);
