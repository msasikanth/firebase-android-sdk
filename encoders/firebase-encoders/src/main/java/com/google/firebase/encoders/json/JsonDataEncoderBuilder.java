// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.encoders.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ValueEncoder;
import com.google.firebase.encoders.ValueEncoderContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class JsonDataEncoderBuilder {

  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders = new HashMap<>();
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders = new HashMap<>();

  private static final class TimestampEncoder implements ValueEncoder<Date> {
    private static final DateFormat rfc339;

    static {
      rfc339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      rfc339.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void encode(@Nullable Date o, @NonNull ValueEncoderContext ctx)
        throws EncodingException, IOException {
      ctx.add(rfc339.format(o));
    }
  }

  private static final ValueEncoder<String> STRING_ENCODER = (o, ctx) -> ctx.add(o);
  private static final ValueEncoder<Integer> INTEGER_ENCODER = (o, ctx) -> ctx.add(o);
  private static final ValueEncoder<Double> DOUBLE_ENCODER = (o, ctx) -> ctx.add(o);
  private static final ValueEncoder<Boolean> BOOLEAN_ENCODER = (o, ctx) -> ctx.add(o);
  private static final TimestampEncoder TIMESTAMP_ENCODER = new TimestampEncoder();

  public JsonDataEncoderBuilder() {
    registerEncoder(String.class, STRING_ENCODER);
    registerEncoder(Integer.class, INTEGER_ENCODER);
    registerEncoder(Double.class, DOUBLE_ENCODER);
    registerEncoder(Boolean.class, BOOLEAN_ENCODER);
    registerEncoder(Date.class, TIMESTAMP_ENCODER);
  }

  @NonNull
  public <T> JsonDataEncoderBuilder registerEncoder(
      @NonNull Class<T> clazz, @NonNull ObjectEncoder<T> objectEncoder) {
    if (objectEncoders.containsKey(clazz)) {
      throw new IllegalArgumentException("Encoder already registered for " + clazz.getName());
    }
    objectEncoders.put(clazz, objectEncoder);
    return this;
  }

  @NonNull
  public <T> JsonDataEncoderBuilder registerEncoder(
      @NonNull Class<T> clazz, @NonNull ValueEncoder<T> encoder) {
    if (valueEncoders.containsKey(clazz)) {
      throw new IllegalArgumentException("Encoder already registered for " + clazz.getName());
    }
    valueEncoders.put(clazz, encoder);
    return this;
  }

  @NonNull
  public DataEncoder build() {
    return new DataEncoder() {
      @Override
      public void encode(@NonNull Object o, @NonNull Writer writer)
          throws IOException, EncodingException {
        JsonValueObjectEncoderContext encoderContext =
            new JsonValueObjectEncoderContext(writer, objectEncoders, valueEncoders);
        encoderContext.add(o);
        encoderContext.close();
      }

      @Override
      public String encode(@NonNull Object o) throws EncodingException {
        StringWriter stringWriter = new StringWriter();
        try {
          encode(o, stringWriter);
        } catch (IOException e) {
          // Should not happen (TM) A StringWriter does not throw IOException.
        }
        return stringWriter.toString();
      }
    };
  }
}
