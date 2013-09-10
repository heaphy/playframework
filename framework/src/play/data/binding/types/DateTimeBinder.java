package play.data.binding.types;

import org.joda.time.DateTime;
import play.data.binding.TypeBinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Binder that support Date class.
 */
public class DateTimeBinder implements TypeBinder<DateTime> {

    private static DateBinder dateBinder = new DateBinder();

    public DateTime bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType) throws Exception {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return new DateTime(dateBinder.bind(name, annotations, value, actualClass, genericType));
    }
}
