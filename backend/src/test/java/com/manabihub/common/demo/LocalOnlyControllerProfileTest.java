package com.manabihub.common.demo;

import com.manabihub.mock.controller.MockJlptRegistryController;
import com.manabihub.mock.controller.MockNationalIdRegistryController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalOnlyControllerProfileTest {

    @Test
    void demoAndMockControllersAreLocalOnly() {
        assertLocalOnly(DemoController.class);
        assertLocalOnly(MockJlptRegistryController.class);
        assertLocalOnly(MockNationalIdRegistryController.class);
    }

    private void assertLocalOnly(Class<?> controllerType) {
        Profile profile = AnnotatedElementUtils.findMergedAnnotation(controllerType, Profile.class);

        assertNotNull(profile, controllerType.getSimpleName() + " must declare an active profile");
        assertArrayEquals(new String[]{"local"}, profile.value());
    }
}
