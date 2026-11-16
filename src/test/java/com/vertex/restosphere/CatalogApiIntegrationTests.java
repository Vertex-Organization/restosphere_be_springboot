package com.vertex.restosphere;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.MenuCategoryRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired MenuItemRepository items;
    @Autowired MenuCategoryRepository categories;
    @Autowired RestaurantTableRepository tables;
    @Autowired AreaRepository areas;

    static RequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @BeforeEach
    void clean() {
        items.deleteAll();
        categories.deleteAll();
        tables.deleteAll();
        areas.deleteAll();
    }

    String create(String url, String json, String role) throws Exception {
        return mockMvc.perform(post(url).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void onlyManagerOrAdminCanWriteAreas() throws Exception {
        mockMvc.perform(post("/api/v1/areas").with(as("WAITER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tang 1\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tang 1\"}")).andExpect(status().isUnauthorized());
        create("/api/v1/areas", "{\"name\":\"Tang 1\"}", "MANAGER");
        mockMvc.perform(post("/api/v1/areas").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tang 1\"}")).andExpect(status().isConflict());
    }

    @Test
    void areaWithTablesCannotBeDeletedAndWaiterCanChangeTableStatus() throws Exception {
        String areaId = JsonPath.read(create("/api/v1/areas", "{\"name\":\"VIP\"}", "MANAGER"), "$.id");
        String tableId = JsonPath.read(create("/api/v1/tables",
                "{\"code\":\"T1\",\"areaId\":\"" + areaId + "\",\"capacity\":4}", "MANAGER"), "$.id");

        mockMvc.perform(delete("/api/v1/areas/" + areaId).with(as("MANAGER"))).andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tables/" + tableId + "/status").with(as("WAITER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OCCUPIED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OCCUPIED"));
        mockMvc.perform(post("/api/v1/tables").with(as("MANAGER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"T2\",\"areaId\":\"" + areaId + "\",\"capacity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicMenuHidesHiddenCategoriesAndDiscontinuedItems() throws Exception {
        String shown = JsonPath.read(create("/api/v1/menu/categories",
                "{\"name\":\"Hai san\",\"sortOrder\":1,\"visible\":true}", "MANAGER"), "$.id");
        String hidden = JsonPath.read(create("/api/v1/menu/categories",
                "{\"name\":\"Noi bo\",\"sortOrder\":2,\"visible\":false}", "MANAGER"), "$.id");
        create("/api/v1/menu/items", "{\"name\":\"Cua\",\"categoryId\":\"" + shown + "\",\"price\":250000}", "MANAGER");
        create("/api/v1/menu/items", "{\"name\":\"Bi mat\",\"categoryId\":\"" + hidden + "\",\"price\":1000}", "MANAGER");

        mockMvc.perform(get("/api/v1/menu/items")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Cua"));
        mockMvc.perform(get("/api/v1/menu/categories")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/menu/items?all=true")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/menu/items?all=true").with(as("WAITER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/menu/items?all=true").with(as("MANAGER")))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(delete("/api/v1/menu/categories/" + shown).with(as("MANAGER")))
                .andExpect(status().isConflict());
    }
}
