/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

/*
 * Copied from http://www.flamingpenguin.co.uk/blog/2012/01/13/hamcrest-regexp-matcher/
 */
package com.vmware.photon.controller.common.tests.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.regex.Pattern;

/**
 * Matchers that use regular expressions.
 *
 * @author t.wood
 */
public class RegexMatcher {
  private abstract static class AbstractRegexpMatcher extends TypeSafeMatcher<String> {
    protected final String regex;
    protected final Pattern compiledRegex;

    private AbstractRegexpMatcher(final String regex) {
      this.regex = regex;
      compiledRegex = Pattern.compile(regex);
    }
  }

  private static class MatchesRegexpMatcher extends AbstractRegexpMatcher {
    private MatchesRegexpMatcher(final String regex) {
      super(regex);
    }

    @Override
    public boolean matchesSafely(final String item) {
      return compiledRegex.matcher(item).matches();
    }

    @Override
    public void describeTo(final Description description) {
      description.appendText("matches regex ").appendValue(regex);
    }
  }

  private static class ContainsMatchRegexpMatcher extends AbstractRegexpMatcher {
    private ContainsMatchRegexpMatcher(final String regex) {
      super(regex);
    }

    @Override
    public boolean matchesSafely(final String item) {
      return compiledRegex.matcher(item).find();
    }

    @Override
    public void describeTo(final Description description) {
      description.appendText("contains match for regex ").appendValue(regex);
    }
  }

  /**
   * Match the regexp against the whole input string.
   *
   * @param regex the regular expression to match
   * @return a matcher which matches the whole input string
   */
  public static Matcher<String> matches(final String regex) {
    return new MatchesRegexpMatcher(regex);
  }

  /**
   * Match the regexp against any substring of the input string.
   *
   * @param regex the regular expression to match
   * @return a matcher which matches anywhere in the input string
   */
  public static Matcher<String> containsMatch(final String regex) {
    return new ContainsMatchRegexpMatcher(regex);
  }
}
