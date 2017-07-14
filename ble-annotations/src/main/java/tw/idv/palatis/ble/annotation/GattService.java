package tw.idv.palatis.ble.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GattService {
    String DEFAULT_CLASS_NAME = "AnnotatedGattServiceFactory";

    String value() default "";

    String factoryClass() default "";
}
