package andrewgrant.friendsdrinks.email;

import static andrewgrant.friendsdrinks.email.ValidationService.PENDING_EMAILS_STORE_NAME;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import andrewgrant.friendsdrinks.avro.*;

/**
 * Validates email request.
 */
public class Validator implements Transformer<EmailId, Request, KeyValue<EmailId, UserEvent>> {

    private KeyValueStore<String, String> pendingEmailsStore;

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        pendingEmailsStore = (KeyValueStore<String, String>) context
                .getStateStore(PENDING_EMAILS_STORE_NAME);
    }

    @Override
    public KeyValue<EmailId, UserEvent> transform(final EmailId emailId,
                                                  final Request request) {
        CreateUserRequest userRequest = request.getCreateUserRequest();
        String requestedEmail = userRequest.getEmail();
        if (pendingEmailsStore.get(requestedEmail) != null) {
            CreateUserRejected userRejected = CreateUserRejected.newBuilder()
                    .setErrorCode(ErrorCode.PENDING.name())
                    .setUserId(userRequest.getUserId())
                    .setEmail(userRequest.getEmail())
                    .setRequestId(userRequest.getRequestId())
                    .build();
            UserEvent user = UserEvent.newBuilder()
                    .setEventType(EventType.CREATE_USER_REJECTED)
                    .setCreateUserRejected(userRejected)
                    .build();
            return new KeyValue<>(emailId, user);
        }
        Email email = request.getCurrEmailState();
        if (email == null) {
            // Add email address to pending state store
            pendingEmailsStore.put(requestedEmail, userRequest.getUserId().getId());
            CreateUserValidated userValidated = CreateUserValidated.newBuilder()
                    .setUserId(userRequest.getUserId())
                    .setEmail(userRequest.getEmail())
                    .setRequestId(userRequest.getRequestId())
                    .build();
            UserEvent user = UserEvent.newBuilder()
                    .setEventType(EventType.CREATE_USER_VALIDATED)
                    .setCreateUserValidated(userValidated)
                    .build();
            return new KeyValue<>(emailId, user);
        } else if (email.getEventType().equals(EmailEvent.RESERVED)) {
            CreateUserRejected userRejected = CreateUserRejected.newBuilder()
                    .setRequestId(userRequest.getRequestId())
                    .setUserId(userRequest.getUserId())
                    .setEmail(userRequest.getEmail())
                    .setErrorCode(ErrorCode.EXISTS.name())
                    .build();
            UserEvent user = UserEvent.newBuilder()
                    .setEventType(EventType.CREATE_USER_REJECTED)
                    .setCreateUserRejected(userRejected)
                    .build();
            return new KeyValue<>(emailId, user);
        }

        throw new RuntimeException();
    }

    @Override
    public void close() {
    }
}