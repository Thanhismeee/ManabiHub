package com.manabihub.security.oauth2;

import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");
        String providerUserId = oauth2User.getAttribute("sub"); // Google provider ID

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        AppUser appUser = appUserRepository.findByEmail(email).orElseGet(() -> {
            log.info("First time Google login detected for email: {}", email);
            Role studentRole = roleRepository.findByCode(RoleCode.STUDENT)
                    .orElseThrow(() -> new IllegalStateException("STUDENT role not found in database"));

            AppUser newUser = AppUser.builder()

                    .email(email)
                    .fullName(name)
                    .avatarUrl(picture)
                    .provider("GOOGLE")

                    .providerUserId(providerUserId)
                    .roles(Set.of(studentRole))
                    .build();
            return appUserRepository.save(java.util.Objects.requireNonNull(newUser));
        });

        if (appUser.getUserStatus() != AccountStatus.ACTIVE) {
            throw new OAuth2AuthenticationException("This ManabiHub account is restricted");
        }

        // Update avatar and name if changed on Google side
        boolean changed = false;
        if (picture != null && !picture.equals(appUser.getAvatarUrl())) {
            appUser.setAvatarUrl(picture);
            changed = true;
        }
        if (name != null && !name.equals(appUser.getFullName())) {
            appUser.setFullName(name);
            changed = true;
        }
        if (changed) {
            appUser = appUserRepository.save(java.util.Objects.requireNonNull(appUser));
        }

        return new CustomOAuth2User(oauth2User, appUser);

    }
}
