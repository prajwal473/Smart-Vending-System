package com.medicinevending.web;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.ActiveProfiles;

/**
 * Safety net: every Spring test in this project must run with the "test" profile (in-memory H2).
 * Several tests run DELETE statements, so a test that forgot the profile could point them at the real
 * MySQL database. This fails the build instead of letting that happen.
 */
class TestIsolationTests {

	@Test
	void everySpringBootTestUsesTheIsolatedTestProfile() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(SpringBootTest.class));

		List<String> found = new ArrayList<>();
		List<String> withoutTestProfile = new ArrayList<>();
		for (var candidate : scanner.findCandidateComponents("com.medicinevending.web")) {
			Class<?> type = Class.forName(candidate.getBeanClassName());
			found.add(type.getSimpleName());
			ActiveProfiles profiles = AnnotatedElementUtils.findMergedAnnotation(type, ActiveProfiles.class);
			boolean usesTest = profiles != null
					&& (List.of(profiles.profiles()).contains("test") || List.of(profiles.value()).contains("test"));
			if (!usesTest) {
				withoutTestProfile.add(type.getName());
			}
		}

		assertThat(found).as("Spring tests found by the scan").isNotEmpty();
		assertThat(withoutTestProfile)
				.as("@SpringBootTest classes missing @ActiveProfiles(\"test\") - they could hit the real MySQL database")
				.isEmpty();
	}
}