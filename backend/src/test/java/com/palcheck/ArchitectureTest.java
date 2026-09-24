package com.palcheck;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * The module boundaries and dependency direction decided in ADR-0018 and docs/MODULES.md section 2a, enforced as
 * tests. They run in {@code mvn test} (no database needed). Java packages are the modules; only {@code api}
 * packages are visible across modules.
 */
@AnalyzeClasses(packages = "com.palcheck", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // --- dependency direction --------------------------------------------------------------------------------

    @ArchTest
    static final ArchRule sharedKernelDependsOnNothingInThisProject = noClasses().that()
            .resideInAPackage("com.palcheck.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.platform..", "com.palcheck.messaging..",
                    "com.palcheck.access..", "com.palcheck.audit..", "com.palcheck.workforce..", "com.palcheck.integrations..");

    @ArchTest
    static final ArchRule platformIsTechnicalAndDependsOnNoModule = noClasses().that()
            .resideInAPackage("com.palcheck.platform..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.messaging..", "com.palcheck.access..",
                    "com.palcheck.audit..", "com.palcheck.workforce..", "com.palcheck.integrations..");

    @ArchTest
    static final ArchRule messagingIsTechnicalAndDependsOnNoDomainModule = noClasses().that()
            .resideInAPackage("com.palcheck.messaging..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.access..", "com.palcheck.audit..",
                    "com.palcheck.workforce..", "com.palcheck.integrations..")
            .because("messaging is technical publication infrastructure, never owned by a domain module (ADR-0018, ADR-0026)");

    @ArchTest
    static final ArchRule auditNeverDependsOnAccessOrWorkforce = noClasses().that()
            .resideInAPackage("com.palcheck.audit..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.access..", "com.palcheck.workforce..",
                    "com.palcheck.integrations..");

    @ArchTest
    static final ArchRule accessNeverDependsOnWorkforce = noClasses().that()
            .resideInAPackage("com.palcheck.access..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.workforce..", "com.palcheck.integrations..");

    @ArchTest
    static final ArchRule workforceNeverDependsOnIntegrations = noClasses().that()
            .resideInAPackage("com.palcheck.workforce..")
            .should().dependOnClassesThat().resideInAPackage("com.palcheck.integrations..")
            .because("integrations writes through workforce.api; workforce must never call back into it (ADR-0024)");

    @ArchTest
    static final ArchRule noKafkaClientOutsideMessagingOrIntegrationsInfrastructure = noClasses().that()
            .resideOutsideOfPackages("com.palcheck.messaging.infrastructure..", "com.palcheck.integrations.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..")
            .because("workforce publishes through OutboxWriter, never a Kafka client directly (ADR-0026); "
                    + "integrations consumes through its own client in its infrastructure package, never owning publication");

    // --- only the api package is visible across modules -----------------------------------------------------

    @ArchTest
    static final ArchRule otherModulesOnlyUseAccessThroughItsApi = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.access..")
            .should().dependOnClassesThat().resideInAPackage("com.palcheck.access.internal..");

    @ArchTest
    static final ArchRule otherModulesOnlyUseAuditThroughItsApi = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.audit..")
            .should().dependOnClassesThat().resideInAPackage("com.palcheck.audit.internal..");

    @ArchTest
    static final ArchRule nothingOutsideWorkforceUsesItsInternals = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.workforce..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.workforce.domain..",
                    "com.palcheck.workforce.application..", "com.palcheck.workforce.infrastructure..",
                    "com.palcheck.workforce.web..");

    @ArchTest
    static final ArchRule nothingOutsideIntegrationsUsesItsInternals = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.integrations..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.integrations.domain..",
                    "com.palcheck.integrations.application..", "com.palcheck.integrations.infrastructure..",
                    "com.palcheck.integrations.web..")
            .because("integrations has no api package yet (ADR-0024): nothing outside it may depend on it at all");

    @ArchTest
    static final ArchRule otherModulesOnlyUseMessagingThroughItsApi = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.messaging..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.messaging.domain..",
                    "com.palcheck.messaging.application..", "com.palcheck.messaging.infrastructure..")
            .because("workforce and integrations may depend only on messaging.api (ADR-0026)");

    @ArchTest
    static final ArchRule integrationsDependsOnlyOnItsAllowedNeighbours = classes().that()
            .resideInAPackage("com.palcheck.integrations..").should().onlyDependOnClassesThat()
            .resideInAnyPackage("com.palcheck.integrations..", "com.palcheck.workforce.api..",
                    "com.palcheck.access.api..", "com.palcheck.audit.api..", "com.palcheck.messaging.api..",
                    "com.palcheck.platform..", "com.palcheck.shared..", "java..", "jakarta..", "org.springframework..",
                    "org.apache.kafka..", "org.slf4j..", "tools.jackson..", "com.fasterxml.jackson.annotation..")
            .because("integrations may write workforce data only through workforce.api and consume events only through messaging.api (ADR-0024, ADR-0026)");

    @ArchTest
    static final ArchRule messagingDependsOnlyOnItsAllowedNeighbours = classes().that()
            .resideInAPackage("com.palcheck.messaging..").should().onlyDependOnClassesThat()
            .resideInAnyPackage("com.palcheck.messaging..", "com.palcheck.platform..", "com.palcheck.shared..",
                    "java..", "jakarta..", "org.springframework..", "org.apache.kafka..", "org.slf4j..",
                    "tools.jackson..", "com.fasterxml.jackson.annotation..")
            .because("messaging is technical infrastructure depending only on shared and platform (ADR-0018, ADR-0026)");

    @ArchTest
    static final ArchRule providerVocabularyNeverLeavesTheAdapterPackage = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.integrations.application.provider..")
            .should().dependOnClassesThat().haveSimpleName("WorkdayWorkerRecord")
            .orShould().dependOnClassesThat().haveSimpleName("WorkdayPullResult")
            .orShould().dependOnClassesThat().haveSimpleName("MockWorkdayProvider")
            .orShould().dependOnClassesThat().haveSimpleName("WorkdayMapper")
            .because("a provider's own field names and codes never leave its adapter package (INTEGRATION_ARCHITECTURE.md principle 2)");

    // --- layering inside the workforce module --------------------------------------------------------------

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that()
            .resideInAPackage("com.palcheck.workforce.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..",
                    "com.fasterxml..", "java.sql..", "com.palcheck.workforce.application..",
                    "com.palcheck.workforce.infrastructure..", "com.palcheck.workforce.web..",
                    "com.palcheck.access..", "com.palcheck.audit..");

    @ArchTest
    static final ArchRule applicationDoesNotKnowInfrastructureOrWeb = noClasses().that()
            .resideInAPackage("com.palcheck.workforce.application..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.workforce.infrastructure..",
                    "com.palcheck.workforce.web..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule controllersNeverTouchRepositoriesOrSql = noClasses().that()
            .resideInAPackage("com.palcheck.workforce.web..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.workforce.infrastructure..",
                    "org.springframework.jdbc..", "java.sql..")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule integrationsDomainIsFrameworkFree = noClasses().that()
            .resideInAPackage("com.palcheck.integrations.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..",
                    "com.fasterxml..", "java.sql..", "com.palcheck.integrations.application..",
                    "com.palcheck.integrations.infrastructure..", "com.palcheck.integrations.web..");

    @ArchTest
    static final ArchRule integrationsApplicationDoesNotKnowInfrastructureOrWeb = noClasses().that()
            .resideInAPackage("com.palcheck.integrations.application..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.integrations.infrastructure..",
                    "com.palcheck.integrations.web..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule messagingApplicationDoesNotKnowInfrastructure = noClasses().that()
            .resideInAPackage("com.palcheck.messaging.application..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.messaging.infrastructure..",
                    "org.springframework.jdbc..", "org.apache.kafka..", "org.springframework.kafka..");

    @ArchTest
    static final ArchRule integrationsControllersNeverTouchRepositoriesOrSql = noClasses().that()
            .resideInAPackage("com.palcheck.integrations.web..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.integrations.infrastructure..",
                    "org.springframework.jdbc..", "java.sql..")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule persistenceStaysInInfrastructure = noClasses().that()
            .resideOutsideOfPackages("..infrastructure..", "..internal..", "com.palcheck.platform..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");

    // --- tenant scoping --------------------------------------------------------------------------------------

    private static final ArchCondition<JavaMethod> TAKE_A_TENANT = new ArchCondition<>("name the tenant") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            if (isScheduledCrossTenantClaim(method)) {
                return;
            }
            boolean names = method.getRawParameterTypes().stream().anyMatch(t -> t.getName().equals("com.palcheck.shared.TenantId")
                    || isTenantOwned(t));
            if (!names) {
                events.add(SimpleConditionEvent.violated(method, method.getFullName()
                        + " reads or writes tenant data without a TenantId (or a tenant-owned entity) parameter"));
            }
        }

        private boolean isTenantOwned(JavaClass type) {
            return (type.getPackageName().equals("com.palcheck.workforce.domain")
                    && type.getSimpleName().matches("Person|Employment|Assignment|LegalEntity|OrgUnit"))
                    || (type.getPackageName().equals("com.palcheck.integrations.domain")
                    && type.getSimpleName().matches("SyncRun|SyncItem|ExternalSystem|FieldLineage"));
        }

        /**
         * A scheduled poller's own claim query must scan every tenant's due work in one statement (ADR-0027,
         * mirrors {@code JdbcOutboxRepository.claimEligible}, which sits outside this rule entirely because
         * {@code messaging.infrastructure} isn't a tenant-scoped domain module). Each row it returns still carries
         * its own {@code TenantId}, which every subsequent call on that row does name.
         */
        private boolean isScheduledCrossTenantClaim(JavaMethod method) {
            return method.getName().equals("claimDueRetries");
        }
    };

    @ArchTest
    static final ArchRule everyWorkforceRepositoryMethodNamesATenant = methods().that()
            .arePublic().and().areDeclaredInClassesThat().resideInAPackage("com.palcheck.workforce.infrastructure..")
            .and().areDeclaredInClassesThat().haveSimpleNameStartingWith("Jdbc")
            .and().areNotStatic()
            .should(TAKE_A_TENANT)
            .because("a query must never be writable without stating whose data it touches");

    @ArchTest
    static final ArchRule everyIntegrationsRepositoryMethodNamesATenant = methods().that()
            .arePublic().and().areDeclaredInClassesThat().resideInAPackage("com.palcheck.integrations.infrastructure..")
            .and().areDeclaredInClassesThat().haveSimpleNameStartingWith("Jdbc")
            .and().areNotStatic()
            .should(TAKE_A_TENANT)
            .because("a query must never be writable without stating whose data it touches");

    // --- trusted actor and provider neutrality ---------------------------------------------------------------

    @ArchTest
    static final ArchRule actorsAreOnlyConstructedByTheAccessModule = noClasses().that()
            .resideOutsideOfPackage("com.palcheck.access..")
            .should().callConstructor(com.palcheck.access.api.Actor.class, java.util.UUID.class,
                    com.palcheck.shared.TenantId.class, String.class, java.util.Set.class)
            .because("the tenant and permissions must come from the trusted principal mapping, never from request data");

    @ArchTest
    static final ArchRule noVendorTypesInProductCode = noClasses().that().resideInAPackage("com.palcheck..")
            .should().dependOnClassesThat().resideInAnyPackage("org.keycloak..", "com.workday..", "com.sap..")
            .because("identity and HR providers are configuration and adapters, never domain dependencies");

    @ArchTest
    static final ArchRule accessDependsOnlyOnItsAllowedNeighbours = classes().that()
            .resideInAPackage("com.palcheck.access..").should().onlyDependOnClassesThat()
            .resideInAnyPackage("com.palcheck.access..", "com.palcheck.audit.api..", "com.palcheck.platform..",
                    "com.palcheck.shared..", "java..", "jakarta..", "org.springframework..", "org.slf4j..",
                    "tools.jackson..", "com.fasterxml.jackson.annotation..");
}
