package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.exception.ProfileNotFoundException;
import fit.iuh.engolearn.user.exception.InvalidDisplayNameException;
import fit.iuh.engolearn.user.model.UserProfile;
import fit.iuh.engolearn.user.repository.UserProfileRepository;
import fit.iuh.engolearn.user.repository.UserProfileNameRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UserProfileServiceImplTest {
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final UserProfileNameRepository names = mock(UserProfileNameRepository.class);
    private final UserProfileServiceImpl service = new UserProfileServiceImpl(profiles, names);

    @Test
    void readsOptionalNameAndAuthoritativeCefrLevel() {
        when(profiles.findById("account")).thenReturn(Optional.of(UserProfile.builder()
                .id("account").displayName("Nguyễn Minh").cefrLevel(CefrLevel.C1).build()));

        var response = service.getCurrentProfile("account");

        assertThat(response.displayName()).isEqualTo("Nguyễn Minh");
        assertThat(response.cefrLevel()).isEqualTo(CefrLevel.C1);
    }

    @Test
    void existingProfileWithoutDisplayNameRemainsReadable() {
        when(profiles.findById("older-account")).thenReturn(Optional.of(UserProfile.builder()
                .id("older-account").cefrLevel(CefrLevel.A2).build()));

        var response = service.getCurrentProfile("older-account");

        assertThat(response.displayName()).isNull();
        assertThat(response.cefrLevel()).isEqualTo(CefrLevel.A2);
    }

    @Test
    void missingLegacyProfileIsNotSilentlyInitialized() {
        when(profiles.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentProfile("missing"))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateTrimsNameAndReturnsCurrentLevel() {
        when(names.updateDisplayName("account", "Nguyễn Minh")).thenReturn(Optional.of(UserProfile.builder()
                .id("account").displayName("Nguyễn Minh").cefrLevel(CefrLevel.B2).build()));

        var response = service.updateDisplayName("account", "  Nguyễn Minh  ");

        assertThat(response.displayName()).isEqualTo("Nguyễn Minh");
        assertThat(response.cefrLevel()).isEqualTo(CefrLevel.B2);
        verify(names).updateDisplayName("account", "Nguyễn Minh");
    }

    @Test
    void nullClearsOptionalName() {
        when(names.updateDisplayName("account", null)).thenReturn(Optional.of(UserProfile.builder()
                .id("account").cefrLevel(CefrLevel.A1).build()));
        assertThat(service.updateDisplayName("account", null).displayName()).isNull();
    }

    @Test
    void invalidNameCannotWrite() {
        assertThatThrownBy(() -> service.updateDisplayName("account", "   "))
                .isInstanceOf(InvalidDisplayNameException.class);
        assertThatThrownBy(() -> service.updateDisplayName("account", "a".repeat(81)))
                .isInstanceOf(InvalidDisplayNameException.class);
        verifyNoInteractions(names);
    }
}
