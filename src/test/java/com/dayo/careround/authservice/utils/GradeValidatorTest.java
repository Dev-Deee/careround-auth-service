package com.dayo.careround.authservice.utils;

import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.exception.InvalidGradeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeValidatorTest {

    @Test
    void validGradesPass() {
        assertThatCode(() -> GradeValidator.validate(UserRole.DOCTOR, "MEDICAL_OFFICER")).doesNotThrowAnyException();
        assertThatCode(() -> GradeValidator.validate(UserRole.NURSE, "STAFF_NURSE")).doesNotThrowAnyException();
        assertThatCode(() -> GradeValidator.validate(UserRole.ADMIN, null)).doesNotThrowAnyException();
    }

    @Test
    void unknownGradeListsAllowedValues() {
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.DOCTOR, "REGISTRAR"))
                .isInstanceOf(InvalidGradeException.class)
                .hasMessageContaining("Invalid grade 'REGISTRAR' for DOCTOR")
                .hasMessageContaining("HOUSE_OFFICER, MEDICAL_OFFICER");
    }

    @Test
    void gradeOfTheOtherRoleIsRejected() {
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.NURSE, "CONSULTANT")).isInstanceOf(InvalidGradeException.class);
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.DOCTOR, "STAFF_NURSE")).isInstanceOf(InvalidGradeException.class);
    }

    @Test
    void missingGradeForClinicalRolesIsRejected() {
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.NURSE, null)).hasMessageContaining("grade is required for NURSE");
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.DOCTOR, " ")).hasMessageContaining("grade is required for DOCTOR");
    }

    @Test
    void lowercaseIsNotAccepted() {
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.NURSE, "staff_nurse")).isInstanceOf(InvalidGradeException.class);
    }

    @Test
    void adminWithAGradeIsRejected() {
        assertThatThrownBy(() -> GradeValidator.validate(UserRole.ADMIN, "CONSULTANT")).hasMessageContaining("ADMIN accounts have no grade");
    }
}
