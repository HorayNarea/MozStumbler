#!/bin/sh
# Usage: ./test.sh <TestClassName>
# Gory details:
# This runs just some of your test cases using a pattern matcher.  In
# genral, you probably just want the test class name.

# See: https://github.com/JCAndKSolutions/android-unit-test for more
# details.

./gradlew copyTestResources
./gradlew testGithubUnittest --tests **$1
