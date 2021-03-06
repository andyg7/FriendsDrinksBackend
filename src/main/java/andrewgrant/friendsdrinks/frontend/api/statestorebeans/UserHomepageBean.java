package andrewgrant.friendsdrinks.frontend.api.statestorebeans;

import java.util.List;

/**
 * Bean for user homepage.
 */
public class UserHomepageBean {
    private String userId;
    private List<FriendsDrinksStateBean> adminFriendsDrinksStateList;
    private List<FriendsDrinksStateBean> memberFriendsDrinksStateList;
    private List<FriendsDrinksInvitationBean> invitationList;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<FriendsDrinksStateBean> getAdminFriendsDrinksStateList() {
        return adminFriendsDrinksStateList;
    }

    public void setAdminFriendsDrinksStateList(List<FriendsDrinksStateBean> adminFriendsDrinksStateList) {
        this.adminFriendsDrinksStateList = adminFriendsDrinksStateList;
    }

    public List<FriendsDrinksStateBean> getMemberFriendsDrinksStateList() {
        return memberFriendsDrinksStateList;
    }

    public void setMemberFriendsDrinksStateList(List<FriendsDrinksStateBean> memberFriendsDrinksStateList) {
        this.memberFriendsDrinksStateList = memberFriendsDrinksStateList;
    }

    public List<FriendsDrinksInvitationBean> getInvitationList() {
        return invitationList;
    }

    public void setInvitationList(List<FriendsDrinksInvitationBean> invitationList) {
        this.invitationList = invitationList;
    }
}
