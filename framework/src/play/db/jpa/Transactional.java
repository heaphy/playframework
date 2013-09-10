package play.db.jpa;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = {ElementType.METHOD, ElementType.TYPE})
public @interface Transactional {
    public boolean readOnly() default false;
}

