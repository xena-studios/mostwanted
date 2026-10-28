package co.xenastudios.mostwanted.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StoreCatalogTest {

    @Test
    void readsTheWebsitesCatalogue() {
        List<StoreCatalog.Item> items = StoreCatalog.parse("""
                {"packages":[
                  {"id":"vip","name":"VIP","category":"ranks","kind":null,"price":4.99,"blurb":"A tag.",
                   "perks":["VIP tag"],"includes":["escape-confetti"]},
                  {"id":"tip-jar","name":"Tip Jar","category":"support","price":1.99,"blurb":"Thanks."}
                ]}""");
        assertEquals(2, items.size());
        assertEquals("vip", items.getFirst().id());
        assertEquals(List.of("VIP tag"), items.getFirst().perks());
        assertEquals("", items.getFirst().kind());
        assertEquals("$4.99", items.getFirst().priceText());
        assertEquals(List.of(), items.get(1).includes());
    }
}
