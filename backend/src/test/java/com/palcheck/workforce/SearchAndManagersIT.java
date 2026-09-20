package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.palcheck.support.WorkforceIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchAndManagersIT extends WorkforceIntegrationTest {
    @Test
    void foldsAccentsAndUnicodeCompositionWithoutChangingStoredNamesOrTenantBoundaries() throws Exception {
        var a = world("accent-a");
        var b = world("accent-b");
        var ids = hire(a, "Jos\u00e9", "Garc\u00eda Sol\u00eds");
        hire(b, "Jos\u00e9", "Garc\u00eda Sol\u00eds");
        for (String q : List.of("Jose", "Garcia", "Solis", "SOLIS", "Jose\u0301")) {
            api.get(a.tenant().admin(), "/api/people?q=" + q)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].personId").value(ids.personId()))
                    .andExpect(jsonPath("$.items[0].displayName").value("Jos\u00e9 Garc\u00eda Sol\u00eds"));
            api.get(a.tenant().admin(), "/api/manager-candidates?q=" + q)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].employmentId").value(ids.employmentId()));
        }
        api.get(a.tenant().admin(), "/api/people/" + ids.personId())
                .andExpect(jsonPath("$.firstName").value("Jos\u00e9"));
        api.get(a.tenant().admin(), "/api/people?q=_").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void pagesAndSearchesBeyondTheFirstHundredAndIncludesEveryEmployment() throws Exception {
        var w = world("manager-pages");
        var first = hire(w, "Ana", "First");
        // Synthetic fixture bulk insert keeps this test focused on lookup, not 101 repeated create journeys.
        jdbc.sql("""
                INSERT INTO workforce.person(id,tenant_id,first_name,last_name,work_email,locale)
                SELECT gen_random_uuid(), :tenant, 'Person', 'Middle' || lpad(n::text,3,'0'),
                       'person' || n || '@example.test','en' FROM generate_series(1,101) n
                """).param("tenant", w.tenantId().value()).update();
        jdbc.sql("""
                INSERT INTO workforce.employment(id,tenant_id,person_id,legal_entity_id,status,employment_type,country,start_date)
                SELECT gen_random_uuid(),tenant_id,id,:entity,'ACTIVE','FULL_TIME','CR','2026-01-15'
                FROM workforce.person WHERE tenant_id=:tenant AND first_name='Person'
                """).param("tenant", w.tenantId().value()).param("entity", w.entity().id()).update();
        var last = hire(w, "Zoe", "Zulu");
        api.get(w.tenant().admin(), "/api/manager-candidates?size=20&page=5")
                .andExpect(jsonPath("$.totalItems").value(103)).andExpect(jsonPath("$.items.length()").value(3));
        api.get(w.tenant().admin(), "/api/manager-candidates?q=Zulu")
                .andExpect(jsonPath("$.items[0].employmentId").value(last.employmentId()));
        api.get(w.tenant().admin(), "/api/manager-candidates?excludingEmploymentId=" + first.employmentId())
                .andExpect(jsonPath("$.totalItems").value(102));
        jdbc.sql("""
                INSERT INTO workforce.employment(id,tenant_id,person_id,legal_entity_id,status,employment_type,country,start_date)
                VALUES(gen_random_uuid(),:tenant,CAST(:person AS uuid),:entity,'PENDING','PART_TIME','CR','2026-02-01')
                """).param("tenant", w.tenantId().value()).param("person", last.personId()).param("entity", w.entity().id()).update();
        api.get(w.tenant().admin(), "/api/manager-candidates?q=Zulu").andExpect(jsonPath("$.totalItems").value(2));
        api.get(w.tenant().admin(), "/api/manager-candidates?size=101").andExpect(status().isBadRequest());
        api.get(w.tenant().admin(), "/api/manager-candidates?page=-1").andExpect(status().isBadRequest());
        api.getAnonymous("/api/manager-candidates").andExpect(status().isUnauthorized());
        api.get(w.tenant().viewer(), "/api/manager-candidates").andExpect(status().isOk());
        var foreign = world("manager-foreign");
        api.get(foreign.tenant().admin(), "/api/manager-candidates?q=Zulu").andExpect(jsonPath("$.totalItems").value(0));
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id=?", w.tenantId().value())).isEqualTo(103);
    }
}
