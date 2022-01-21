package ru.qa.tinkoff.steps;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"ru.qa.tinkoff.steps.trackingSlaveSteps", "ru.qa.tinkoff.creator"})

public class StpTrackingSlaveStepsConfiguration {
}
