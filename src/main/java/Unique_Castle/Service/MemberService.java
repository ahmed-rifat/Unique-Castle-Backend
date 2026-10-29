package Unique_Castle.Service;

import Unique_Castle.Entity.MemberAuditLog;
import Unique_Castle.RequestData.FlatMemberRequest;
import Unique_Castle.RequestData.ResetPasswordRequest;
import Unique_Castle.ResponseData.FlatMemberResponse;

import java.util.List;
import java.util.Map;

public interface MemberService {
    List<FlatMemberResponse> getAllMembers();
    FlatMemberResponse getMemberById(Long id);
    FlatMemberResponse createMember(FlatMemberRequest request, String adminUsername);
    FlatMemberResponse updateMember(Long id, FlatMemberRequest request, String adminUsername);
    FlatMemberResponse toggleAccountStatus(Long id, String status, String adminUsername);
    Map<String, Object> resetPassword(Long id, ResetPasswordRequest request, String adminUsername);
    Map<String, Object> deleteOrArchiveMember(Long id, String adminUsername);
    List<MemberAuditLog> getAuditLogs();
}
