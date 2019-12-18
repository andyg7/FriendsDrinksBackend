package andrewgrant.friendsdrinks.fraud;

import andrewgrant.friendsdrinks.user.avro.CreateUserRequest;

/**
 * Class that represents a user request.
 */
public class FraudTracker {
    private CreateUserRequest request;
    private Long count;

    public FraudTracker(CreateUserRequest request, Long count) {
        this.request = request;
        this.count = count;
    }

    public CreateUserRequest getRequest() {
        return request;
    }

    public Long getCount() {
        return count;
    }
}

