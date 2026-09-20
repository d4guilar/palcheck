package com.palcheck.support;

import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.workforce.application.ReferenceDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Base class for API integration tests: a real context and database, an {@link Api} client, and a fresh world per test. */
@PalCheckIntegrationTest
public abstract class WorkforceIntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected TenantProvisioning provisioning;
    @Autowired
    protected ReferenceDataRepository reference;
    @Autowired
    protected JdbcClient jdbc;

    protected Api api;

    @BeforeEach
    void createApi() {
        api = new Api(mvc, json);
    }

    protected WorkforceWorld world(String label) {
        return WorkforceWorld.create(provisioning, reference, label);
    }

    protected long count(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (int i = 0; i < params.length; i++) {
            spec = spec.param(params[i]);
        }
        return spec.query(Long.class).single();
    }

    /** Identifiers of a freshly created workforce record. */
    protected record Ids(String personId, String employmentId, String assignmentId) {
    }

    /** Creates a person (with employment and assignment) as the world's administrator. */
    protected Ids hire(WorkforceWorld w, String first, String last) throws Exception {
        return hire(w, first, last, Api.uniqueEmail(first.toLowerCase()));
    }

    protected Ids hire(WorkforceWorld w, String first, String last, String email) throws Exception {
        String personId = api.read(api.post(w.tenant().admin(), "/api/people", Api.createBody(w, first, last, email),
                Api.newKey()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()))
                .read("$.id");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        return new Ids(personId, employments.read("$.items[0].id"), employments.read("$.items[0].currentAssignment.id"));
    }
}
