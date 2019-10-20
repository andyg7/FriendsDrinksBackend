/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package andrewgrant.friendsdrinks.avro;

import org.apache.avro.specific.SpecificData;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class FriendsDrinks extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -757978358350449612L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"FriendsDrinks\",\"namespace\":\"andrewgrant.friendsdrinks.avro\",\"fields\":[{\"name\":\"requestId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"requesterUserId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"userIds\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}},{\"name\":\"schedule\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<FriendsDrinks> ENCODER =
      new BinaryMessageEncoder<FriendsDrinks>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<FriendsDrinks> DECODER =
      new BinaryMessageDecoder<FriendsDrinks>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   */
  public static BinaryMessageDecoder<FriendsDrinks> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   */
  public static BinaryMessageDecoder<FriendsDrinks> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<FriendsDrinks>(MODEL$, SCHEMA$, resolver);
  }

  /** Serializes this FriendsDrinks to a ByteBuffer. */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /** Deserializes a FriendsDrinks from a ByteBuffer. */
  public static FriendsDrinks fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public java.lang.String requestId;
  @Deprecated public java.lang.String requesterUserId;
  @Deprecated public java.util.List<java.lang.String> userIds;
  @Deprecated public java.lang.String schedule;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public FriendsDrinks() {}

  /**
   * All-args constructor.
   * @param requestId The new value for requestId
   * @param requesterUserId The new value for requesterUserId
   * @param userIds The new value for userIds
   * @param schedule The new value for schedule
   */
  public FriendsDrinks(java.lang.String requestId, java.lang.String requesterUserId, java.util.List<java.lang.String> userIds, java.lang.String schedule) {
    this.requestId = requestId;
    this.requesterUserId = requesterUserId;
    this.userIds = userIds;
    this.schedule = schedule;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return requestId;
    case 1: return requesterUserId;
    case 2: return userIds;
    case 3: return schedule;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: requestId = (java.lang.String)value$; break;
    case 1: requesterUserId = (java.lang.String)value$; break;
    case 2: userIds = (java.util.List<java.lang.String>)value$; break;
    case 3: schedule = (java.lang.String)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'requestId' field.
   * @return The value of the 'requestId' field.
   */
  public java.lang.String getRequestId() {
    return requestId;
  }

  /**
   * Sets the value of the 'requestId' field.
   * @param value the value to set.
   */
  public void setRequestId(java.lang.String value) {
    this.requestId = value;
  }

  /**
   * Gets the value of the 'requesterUserId' field.
   * @return The value of the 'requesterUserId' field.
   */
  public java.lang.String getRequesterUserId() {
    return requesterUserId;
  }

  /**
   * Sets the value of the 'requesterUserId' field.
   * @param value the value to set.
   */
  public void setRequesterUserId(java.lang.String value) {
    this.requesterUserId = value;
  }

  /**
   * Gets the value of the 'userIds' field.
   * @return The value of the 'userIds' field.
   */
  public java.util.List<java.lang.String> getUserIds() {
    return userIds;
  }

  /**
   * Sets the value of the 'userIds' field.
   * @param value the value to set.
   */
  public void setUserIds(java.util.List<java.lang.String> value) {
    this.userIds = value;
  }

  /**
   * Gets the value of the 'schedule' field.
   * @return The value of the 'schedule' field.
   */
  public java.lang.String getSchedule() {
    return schedule;
  }

  /**
   * Sets the value of the 'schedule' field.
   * @param value the value to set.
   */
  public void setSchedule(java.lang.String value) {
    this.schedule = value;
  }

  /**
   * Creates a new FriendsDrinks RecordBuilder.
   * @return A new FriendsDrinks RecordBuilder
   */
  public static andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder newBuilder() {
    return new andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder();
  }

  /**
   * Creates a new FriendsDrinks RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new FriendsDrinks RecordBuilder
   */
  public static andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder newBuilder(andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder other) {
    return new andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder(other);
  }

  /**
   * Creates a new FriendsDrinks RecordBuilder by copying an existing FriendsDrinks instance.
   * @param other The existing instance to copy.
   * @return A new FriendsDrinks RecordBuilder
   */
  public static andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder newBuilder(andrewgrant.friendsdrinks.avro.FriendsDrinks other) {
    return new andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder(other);
  }

  /**
   * RecordBuilder for FriendsDrinks instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<FriendsDrinks>
    implements org.apache.avro.data.RecordBuilder<FriendsDrinks> {

    private java.lang.String requestId;
    private java.lang.String requesterUserId;
    private java.util.List<java.lang.String> userIds;
    private java.lang.String schedule;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.requestId)) {
        this.requestId = data().deepCopy(fields()[0].schema(), other.requestId);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.requesterUserId)) {
        this.requesterUserId = data().deepCopy(fields()[1].schema(), other.requesterUserId);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.userIds)) {
        this.userIds = data().deepCopy(fields()[2].schema(), other.userIds);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.schedule)) {
        this.schedule = data().deepCopy(fields()[3].schema(), other.schedule);
        fieldSetFlags()[3] = true;
      }
    }

    /**
     * Creates a Builder by copying an existing FriendsDrinks instance
     * @param other The existing instance to copy.
     */
    private Builder(andrewgrant.friendsdrinks.avro.FriendsDrinks other) {
            super(SCHEMA$);
      if (isValidValue(fields()[0], other.requestId)) {
        this.requestId = data().deepCopy(fields()[0].schema(), other.requestId);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.requesterUserId)) {
        this.requesterUserId = data().deepCopy(fields()[1].schema(), other.requesterUserId);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.userIds)) {
        this.userIds = data().deepCopy(fields()[2].schema(), other.userIds);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.schedule)) {
        this.schedule = data().deepCopy(fields()[3].schema(), other.schedule);
        fieldSetFlags()[3] = true;
      }
    }

    /**
      * Gets the value of the 'requestId' field.
      * @return The value.
      */
    public java.lang.String getRequestId() {
      return requestId;
    }

    /**
      * Sets the value of the 'requestId' field.
      * @param value The value of 'requestId'.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder setRequestId(java.lang.String value) {
      validate(fields()[0], value);
      this.requestId = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'requestId' field has been set.
      * @return True if the 'requestId' field has been set, false otherwise.
      */
    public boolean hasRequestId() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'requestId' field.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder clearRequestId() {
      requestId = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'requesterUserId' field.
      * @return The value.
      */
    public java.lang.String getRequesterUserId() {
      return requesterUserId;
    }

    /**
      * Sets the value of the 'requesterUserId' field.
      * @param value The value of 'requesterUserId'.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder setRequesterUserId(java.lang.String value) {
      validate(fields()[1], value);
      this.requesterUserId = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'requesterUserId' field has been set.
      * @return True if the 'requesterUserId' field has been set, false otherwise.
      */
    public boolean hasRequesterUserId() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'requesterUserId' field.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder clearRequesterUserId() {
      requesterUserId = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'userIds' field.
      * @return The value.
      */
    public java.util.List<java.lang.String> getUserIds() {
      return userIds;
    }

    /**
      * Sets the value of the 'userIds' field.
      * @param value The value of 'userIds'.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder setUserIds(java.util.List<java.lang.String> value) {
      validate(fields()[2], value);
      this.userIds = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'userIds' field has been set.
      * @return True if the 'userIds' field has been set, false otherwise.
      */
    public boolean hasUserIds() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'userIds' field.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder clearUserIds() {
      userIds = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'schedule' field.
      * @return The value.
      */
    public java.lang.String getSchedule() {
      return schedule;
    }

    /**
      * Sets the value of the 'schedule' field.
      * @param value The value of 'schedule'.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder setSchedule(java.lang.String value) {
      validate(fields()[3], value);
      this.schedule = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'schedule' field has been set.
      * @return True if the 'schedule' field has been set, false otherwise.
      */
    public boolean hasSchedule() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'schedule' field.
      * @return This builder.
      */
    public andrewgrant.friendsdrinks.avro.FriendsDrinks.Builder clearSchedule() {
      schedule = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FriendsDrinks build() {
      try {
        FriendsDrinks record = new FriendsDrinks();
        record.requestId = fieldSetFlags()[0] ? this.requestId : (java.lang.String) defaultValue(fields()[0]);
        record.requesterUserId = fieldSetFlags()[1] ? this.requesterUserId : (java.lang.String) defaultValue(fields()[1]);
        record.userIds = fieldSetFlags()[2] ? this.userIds : (java.util.List<java.lang.String>) defaultValue(fields()[2]);
        record.schedule = fieldSetFlags()[3] ? this.schedule : (java.lang.String) defaultValue(fields()[3]);
        return record;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<FriendsDrinks>
    WRITER$ = (org.apache.avro.io.DatumWriter<FriendsDrinks>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<FriendsDrinks>
    READER$ = (org.apache.avro.io.DatumReader<FriendsDrinks>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}
