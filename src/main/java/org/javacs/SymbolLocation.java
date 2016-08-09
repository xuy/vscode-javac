package org.javacs;

import io.typefox.lsapi.LocationImpl;
import io.typefox.lsapi.RangeImpl;

import javax.tools.JavaFileObject;
import java.net.URI;

public class SymbolLocation {
    public final JavaFileObject file;
    public final long startPosition, endPosition;

    public SymbolLocation(JavaFileObject file, long startPosition, long endPosition) {
        this.file = file;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public LocationImpl location() {
        URI uri = this.file.toUri();
        RangeImpl range = JavaLanguageServer.findPosition(this.file, this.startPosition, this.endPosition);
        LocationImpl location = new LocationImpl();

        location.setRange(range);
        location.setUri(uri.toString());
        return location;
    }
}
