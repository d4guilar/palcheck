package com.palcheck.integrations.web;

import com.palcheck.access.api.Actor;
import com.palcheck.integrations.application.ExternalSystemService;
import com.palcheck.integrations.application.SyncRunService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The systems catalog, running a sync, and sync history (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 6). */
@RestController
@RequestMapping("/api")
class SystemsController {

    private final boolean scenariosEnabled;
    private final ExternalSystemService systems;
    private final SyncRunService syncRuns;

    SystemsController(ExternalSystemService systems, SyncRunService syncRuns, org.springframework.core.env.Environment environment) {
        this.scenariosEnabled = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "test"));
        this.systems = systems;
        this.syncRuns = syncRuns;
    }

    @GetMapping("/systems")
    Dtos.PageDto<Dtos.SystemDto> list(Actor actor) {
        List<Dtos.SystemDto> items = systems.list(actor).stream().map(s -> Dtos.system(s, scenariosEnabled)).toList();
        return new Dtos.PageDto<>(items, 0, items.size(), items.size(), items.isEmpty() ? 0 : 1);
    }

    @GetMapping("/systems/{code}")
    Dtos.SystemDto get(Actor actor, @PathVariable String code) {
        return Dtos.system(systems.get(actor, code), scenariosEnabled);
    }

    @PostMapping("/systems/{code}/sync")
    Dtos.SyncRunDto sync(Actor actor, @PathVariable String code,
            @RequestParam(defaultValue = "BASELINE") String scenario) {
        if (!scenario.equals("BASELINE") && (!scenariosEnabled || !scenario.equals("TITLE_CHANGE"))) {
            throw new com.palcheck.shared.ValidationException(new com.palcheck.shared.FieldViolation("scenario", "INVALID", "This simulation scenario is unavailable."));
        }
        return Dtos.syncRun(syncRuns.runSync(actor, code, new com.palcheck.integrations.application.provider.WorkdayPullOptions(scenario.equals("TITLE_CHANGE"), false, false)));
    }

    @GetMapping("/systems/{code}/syncs")
    Dtos.PageDto<Dtos.SyncRunDto> syncs(Actor actor, @PathVariable String code,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return Dtos.page(syncRuns.listRuns(actor, code, page, size), Dtos::syncRun);
    }

    @GetMapping("/syncs/{runId}/summary")
    Dtos.SyncRunDto summary(Actor actor, @PathVariable UUID runId) {
        return Dtos.syncRun(syncRuns.getRun(actor, runId));
    }

    @GetMapping("/syncs/{runId}")
    Dtos.PageDto<Dtos.SyncItemDto> syncItems(Actor actor, @PathVariable UUID runId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return Dtos.page(syncRuns.listItems(actor, runId, page, size), Dtos::syncItem);
    }
}
