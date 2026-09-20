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
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.platform..", "com.palcheck.access..",
                    "com.palcheck.audit..", "com.palcheck.workforce..");

    @ArchTest
    static final ArchRule platformIsTechnicalAndDependsOnNoModule = noClasses().that()
            .resideInAPackage("com.palcheck.platform..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.access..", "com.palcheck.audit..",
                    "com.palcheck.workforce..");

    @ArchTest
    static final ArchRule auditNeverDependsOnAccessOrWorkforce = noClasses().that()
            .resideInAPackage("com.palcheck.audit..")
            .should().dependOnClassesThat().resideInAnyPackage("com.palcheck.access..", "com.palcheck.workforce..");

    @ArchTest
    static final ArchRule accessNeverDependsOnWorkforce = noClasses().that()
            .resideInAPackage("com.palcheck.access..")
            .should().dependOnClassesThat().resideInAPackage("com.palcheck.workforce..");

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
    static final ArchRule persistenceStaysInInfrastructure = noClasses().that()
            .resideOutsideOfPackages("..infrastructure..", "..internal..", "com.palcheck.platform..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");

    // --- tenant scoping --------------------------------------------------------------------------------------

    private static final ArchCondition<JavaMethod> TAKE_A_TENANT = new ArchCondition<>("name the tenant") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            boolean names = method.getRawParameterTypes().stream().anyMatch(t -> t.getName().equals("com.palcheck.shared.TenantId")
                    || isTenantOwned(t));
            if (!names) {
                events.add(SimpleConditionEvent.violated(method, method.getFullName()
                        + " reads or writes tenant data without a TenantId (or a tenant-owned entity) parameter"));
            }
        }

        private boolean isTenantOwned(JavaClass type) {
            return type.getPackageName().equals("com.palcheck.workforce.domain")
                    && type.getSimpleName().matches("Person|Employment|Assignment|LegalEntity|OrgUnit");
        }
    };

    @ArchTest
    static final ArchRule everyWorkforceRepositoryMethodNamesATenant = methods().that()
            .arePublic().and().areDeclaredInClassesThat().resideInAPackage("com.palcheck.workforce.infrastructure..")
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
