package hopperOptimizations.annotation;

public @interface BugFix {
    String value();

    String fixVersion() default "";

    String status() default "";
}
