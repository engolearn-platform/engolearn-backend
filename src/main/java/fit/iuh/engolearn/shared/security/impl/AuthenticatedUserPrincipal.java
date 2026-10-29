package fit.iuh.engolearn.shared.security.impl;

import fit.iuh.engolearn.shared.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AuthenticatedUserPrincipal implements UserDetails {

    private final String id;
    private final String email;
    private final UserRole role;
    private final Set<GrantedAuthority> authorities;

    public AuthenticatedUserPrincipal(String id, String email, UserRole role) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.authorities = buildAuthorities(role);
    }

    private Set<GrantedAuthority> buildAuthorities(UserRole role) {
        Set<GrantedAuthority> auths = new HashSet<>();
        if (role == UserRole.USER || role == UserRole.CONTENT_MANAGER || role == UserRole.ADMIN) {
            auths.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        if (role == UserRole.CONTENT_MANAGER || role == UserRole.ADMIN) {
            auths.add(new SimpleGrantedAuthority("ROLE_CONTENT_MANAGER"));
        }
        if (role == UserRole.ADMIN) {
            auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return auths;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
