package com.palcheck.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/** A thin, readable client over MockMvc for the workforce API, signing requests in as a given test person. */
public final class Api {

    private final MockMvc mvc;
    private final JsonMapper json;

    public Api(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public ResultActions get(TestTenant.Person as, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).with(as.login()));
    }

    public ResultActions getAnonymous(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url));
    }

    /** POST or PATCH with a JSON body and an optional Idempotency-Key. */
    public ResultActions send(HttpMethod method, TestTenant.Person as, String url, Object body, String idempotencyKey)
            throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, url)
                .with(as.login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : json.writeValueAsString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    /** A mutating request with a valid CSRF token but no authenticated session. */
    public ResultActions sendAnonymous(HttpMethod method, String url, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.request(method, url).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : json.writeValueAsString(body)));
    }

    public ResultActions post(TestTenant.Person as, String url, Object body, String idempotencyKey) throws Exception {
        return send(HttpMethod.POST, as, url, body, idempotencyKey);
    }

    public ResultActions patch(TestTenant.Person as, String url, Object body) throws Exception {
        return send(HttpMethod.PATCH, as, url, body, null);
    }

    public ResultActions patch(TestTenant.Person as, String url, Object body, String idempotencyKey) throws Exception {
        return send(HttpMethod.PATCH, as, url, body, idempotencyKey);
    }

    public DocumentContext read(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString());
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** A valid create-request body for the world, with the given identity. Mutate the returned maps freely. */
    public static Map<String, Object> createBody(WorkforceWorld world, String first, String last, String email) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("firstName", first);
        person.put("lastName", last);
        person.put("workEmail", email);
        person.put("locale", "en");
        Map<String, Object> employment = new LinkedHashMap<>();
        employment.put("legalEntityId", world.entity().id().toString());
        employment.put("status", "ACTIVE");
        employment.put("employmentType", "FULL_TIME");
        employment.put("country", "CR");
        employment.put("startDate", "2026-01-15");
        Map<String, Object> assignment = new LinkedHashMap<>();
        assignment.put("title", "Software Engineer");
        assignment.put("orgUnitId", world.unit().id().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("person", person);
        body.put("employment", employment);
        body.put("assignment", assignment);
        return body;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> section(Map<String, Object> body, String name) {
        return (Map<String, Object>) body.get(name);
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }
}
