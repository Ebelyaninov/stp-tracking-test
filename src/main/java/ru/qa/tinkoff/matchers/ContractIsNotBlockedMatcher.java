package ru.qa.tinkoff.matchers;

import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import ru.qa.tinkoff.tracking.entities.Contract;

public class ContractIsNotBlockedMatcher extends TypeSafeMatcher<Contract> {
    @Override
    protected boolean matchesSafely(Contract contract) {
        return !contract.getBlocked();
    }

    @Override
    public void describeTo(org.hamcrest.Description description) {
        description.appendText("Contract is not blocked");
    }

    public static Matcher<Contract> contractIsNotBlockedMatcher() {
        return new ContractIsNotBlockedMatcher();
    }
}
