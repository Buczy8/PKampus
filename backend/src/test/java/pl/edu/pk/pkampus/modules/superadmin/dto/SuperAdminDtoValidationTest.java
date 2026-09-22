package pl.edu.pk.pkampus.modules.superadmin.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.events.DormEventCategory;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SuperAdmin DTO Validation Tests")
class SuperAdminDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("CreateDormitoryRequestDto validation")
    class CreateDormitoryRequestDtoTests {

        @Test
        @DisplayName("Valid DTO produces no violations")
        void validDtoProducesNoViolations() {
            // Arrange
            CreateDormitoryRequestDto dto = CreateDormitoryRequestDto.builder()
                    .code("DS1")
                    .name("Dormitory 1")
                    .address("ul. Akademicka 1")
                    .floorsCount(4)
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormitoryRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Blank fields produce violations")
        void blankFieldsProduceViolations() {
            // Arrange
            CreateDormitoryRequestDto dto = CreateDormitoryRequestDto.builder()
                    .code("   ")
                    .name("")
                    .address(null)
                    .floorsCount(null)
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormitoryRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(4);
            assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .containsExactlyInAnyOrder("code", "name", "address", "floorsCount");
        }

        @Test
        @DisplayName("Field size bounds produce violations")
        void fieldSizeBoundsProduceViolations() {
            // Arrange
            CreateDormitoryRequestDto dto = CreateDormitoryRequestDto.builder()
                    .code("TOOLONGCODE1")
                    .name("A".repeat(101))
                    .address("A".repeat(256))
                    .floorsCount(0)
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormitoryRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(4);
            assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .containsExactlyInAnyOrder("code", "name", "address", "floorsCount");
        }

        @Test
        @DisplayName("Floors count exceeding 50 produces violation")
        void floorsCountExceedingMaxProducesViolation() {
            // Arrange
            CreateDormitoryRequestDto dto = CreateDormitoryRequestDto.builder()
                    .code("DS1")
                    .name("Dormitory 1")
                    .address("ul. Akademicka 1")
                    .floorsCount(51)
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormitoryRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("floorsCount");
        }
    }

    @Nested
    @DisplayName("UpdateDormitoryRequestDto validation")
    class UpdateDormitoryRequestDtoTests {

        @Test
        @DisplayName("Valid empty or filled DTO produces no violations")
        void validDtoProducesNoViolations() {
            // Arrange
            UpdateDormitoryRequestDto emptyDto = UpdateDormitoryRequestDto.builder().build();
            UpdateDormitoryRequestDto filledDto = UpdateDormitoryRequestDto.builder()
                    .code("DS2")
                    .name("Dorm 2")
                    .address("ul. Akademicka 2")
                    .floorsCount(5)
                    .laundryOpeningTime(LocalTime.of(8, 0))
                    .laundryClosingTime(LocalTime.of(22, 0))
                    .laundrySlotDurationMinutes(120)
                    .build();

            // Act & Assert
            assertThat(validator.validate(emptyDto)).isEmpty();
            assertThat(validator.validate(filledDto)).isEmpty();
        }

        @Test
        @DisplayName("Field size bounds and slot durations produce violations")
        void invalidFieldsProduceViolations() {
            // Arrange
            UpdateDormitoryRequestDto dto = UpdateDormitoryRequestDto.builder()
                    .code("12345678901")
                    .name("A".repeat(101))
                    .address("A".repeat(256))
                    .floorsCount(51)
                    .laundrySlotDurationMinutes(29)
                    .build();

            // Act
            Set<ConstraintViolation<UpdateDormitoryRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(5);
            assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .containsExactlyInAnyOrder("code", "name", "address", "floorsCount", "laundrySlotDurationMinutes");
        }

        @Test
        @DisplayName("Laundry slot duration exceeding max produces violation")
        void laundrySlotDurationExceedingMaxProducesViolation() {
            // Arrange
            UpdateDormitoryRequestDto dto = UpdateDormitoryRequestDto.builder()
                    .laundrySlotDurationMinutes(481)
                    .build();

            // Act
            Set<ConstraintViolation<UpdateDormitoryRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("laundrySlotDurationMinutes");
        }
    }

    @Nested
    @DisplayName("CreateDormAdminRequestDto validation")
    class CreateDormAdminRequestDtoTests {

        @Test
        @DisplayName("Valid DTO produces no violations")
        void validDtoProducesNoViolations() {
            // Arrange
            CreateDormAdminRequestDto dto = CreateDormAdminRequestDto.builder()
                    .firstName("Jan")
                    .lastName("Kowalski")
                    .email("jan.kowalski@pk.edu.pl")
                    .phoneNumber("+48123456789")
                    .password("SecurePass1!")
                    .dormitoryId(UUID.randomUUID())
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormAdminRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Blank fields produce violations")
        void blankFieldsProduceViolations() {
            // Arrange
            CreateDormAdminRequestDto dto = CreateDormAdminRequestDto.builder()
                    .firstName("")
                    .lastName("   ")
                    .email(null)
                    .phoneNumber("")
                    .password(null)
                    .dormitoryId(null)
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormAdminRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).isNotEmpty();
            assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .contains("firstName", "lastName", "email", "phoneNumber", "password", "dormitoryId");
        }

        @Test
        @DisplayName("Invalid phone number produces violation")
        void invalidPhoneNumberProducesViolation() {
            // Arrange
            CreateDormAdminRequestDto dto = CreateDormAdminRequestDto.builder()
                    .firstName("Jan")
                    .lastName("Kowalski")
                    .email("jan.kowalski@pk.edu.pl")
                    .phoneNumber("abc123")
                    .password("SecurePass1!")
                    .dormitoryId(UUID.randomUUID())
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormAdminRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("phoneNumber");
        }

        @Test
        @DisplayName("Weak password produces violation")
        void weakPasswordProducesViolation() {
            // Arrange
            CreateDormAdminRequestDto dto = CreateDormAdminRequestDto.builder()
                    .firstName("Jan")
                    .lastName("Kowalski")
                    .email("jan.kowalski@pk.edu.pl")
                    .phoneNumber("+48123456789")
                    .password("simplepass")
                    .dormitoryId(UUID.randomUUID())
                    .build();

            // Act
            Set<ConstraintViolation<CreateDormAdminRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("password");
        }
    }

    @Nested
    @DisplayName("UpdateDormAdminRequestDto validation")
    class UpdateDormAdminRequestDtoTests {

        @Test
        @DisplayName("Valid DTO produces no violations")
        void validDtoProducesNoViolations() {
            // Arrange
            UpdateDormAdminRequestDto dto = UpdateDormAdminRequestDto.builder()
                    .firstName("Adam")
                    .lastName("Nowak")
                    .phoneNumber("+48987654321")
                    .status(UserStatus.ACTIVE)
                    .dormitoryId(UUID.randomUUID())
                    .build();

            // Act
            Set<ConstraintViolation<UpdateDormAdminRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Exceeding size limits produces violations")
        void exceedingSizeLimitsProducesViolations() {
            // Arrange
            UpdateDormAdminRequestDto dto = UpdateDormAdminRequestDto.builder()
                    .firstName("A".repeat(51))
                    .lastName("B".repeat(81))
                    .phoneNumber("C".repeat(21))
                    .build();

            // Act
            Set<ConstraintViolation<UpdateDormAdminRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(3);
            assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .containsExactlyInAnyOrder("firstName", "lastName", "phoneNumber");
        }
    }

    @Nested
    @DisplayName("CreateCampusEventRequestDto validation")
    class CreateCampusEventRequestDtoTests {

        @Test
        @DisplayName("Valid DTO produces no violations")
        void validDtoProducesNoViolations() {
            // Arrange
            CreateCampusEventRequestDto dto = CreateCampusEventRequestDto.builder()
                    .title("Campus Announcement")
                    .description("Important update for all students.")
                    .category(DormEventCategory.ADMIN_NOTICE)
                    .priority(DormEventPriority.INFO)
                    .pinned(true)
                    .eventDate(Instant.now())
                    .endDate(Instant.now().plusSeconds(3600))
                    .build();

            // Act
            Set<ConstraintViolation<CreateCampusEventRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Blank and null required fields produce violations")
        void requiredFieldsProduceViolations() {
            // Arrange
            CreateCampusEventRequestDto dto = CreateCampusEventRequestDto.builder()
                    .title("   ")
                    .description(null)
                    .category(null)
                    .priority(null)
                    .eventDate(null)
                    .build();

            // Act
            Set<ConstraintViolation<CreateCampusEventRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(5);
            assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .containsExactlyInAnyOrder("title", "description", "category", "priority", "eventDate");
        }

        @Test
        @DisplayName("Title exceeding max produces violation")
        void titleExceedingMaxProducesViolation() {
            // Arrange
            CreateCampusEventRequestDto dto = CreateCampusEventRequestDto.builder()
                    .title("A".repeat(201))
                    .description("Description")
                    .category(DormEventCategory.ADMIN_NOTICE)
                    .priority(DormEventPriority.INFO)
                    .eventDate(Instant.now())
                    .build();

            // Act
            Set<ConstraintViolation<CreateCampusEventRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("title");
        }
    }

    @Nested
    @DisplayName("UpdateCampusEventRequestDto validation")
    class UpdateCampusEventRequestDtoTests {

        @Test
        @DisplayName("Valid empty or filled DTO produces no violations")
        void validDtoProducesNoViolations() {
            // Arrange
            UpdateCampusEventRequestDto emptyDto = UpdateCampusEventRequestDto.builder().build();
            UpdateCampusEventRequestDto filledDto = UpdateCampusEventRequestDto.builder()
                    .title("Updated Title")
                    .description("Updated Desc")
                    .category(DormEventCategory.BED_LINEN)
                    .priority(DormEventPriority.WARNING)
                    .pinned(false)
                    .eventDate(Instant.now())
                    .endDate(Instant.now().plusSeconds(7200))
                    .build();

            // Act & Assert
            assertThat(validator.validate(emptyDto)).isEmpty();
            assertThat(validator.validate(filledDto)).isEmpty();
        }

        @Test
        @DisplayName("Title exceeding max produces violation")
        void titleExceedingMaxProducesViolation() {
            // Arrange
            UpdateCampusEventRequestDto dto = UpdateCampusEventRequestDto.builder()
                    .title("A".repeat(201))
                    .build();

            // Act
            Set<ConstraintViolation<UpdateCampusEventRequestDto>> violations = validator.validate(dto);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("title");
        }
    }
}
