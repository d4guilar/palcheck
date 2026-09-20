package com.palcheck.access.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** What each role may do is defined in code and reviewed like code; these tests pin the intended matrix. */
class RoleTest {

    @Test
    void anHrAdministratorHoldsEveryPermission() {
        assertThat(Role.HR_ADMIN.permissions()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void anHrViewerCanReadPeopleAndEmploymentButNeverChangeOrSeeHistory() {
        assertThat(Role.HR_VIEWER.permissions()).containsExactlyInAnyOrder(
                Permission.VIEW_PEOPLE, Permission.VIEW_PERSON, Permission.VIEW_EMPLOYMENT);
        assertThat(Role.HR_VIEWER.permissions()).doesNotContain(
                Permission.CREATE_PERSON, Permission.EDIT_PERSON, Permission.EDIT_EMPLOYMENT,
                Permission.VIEW_AUDIT_HISTORY);
    }

    @Test
    void everyPermissionIsGrantedByAtLeastOneRoleSoNoneIsDeadCode() {
        var granted = EnumSet.noneOf(Permission.class);
        Arrays.stream(Role.values()).forEach(role -> granted.addAll(role.permissions()));

        assertThat(granted).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void thePermissionSetOfARoleCannotBeModifiedAtRuntime() {
        var permissions = Role.HR_VIEWER.permissions();

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> permissions.add(Permission.EDIT_PERSON));
    }
}
