package com.rag.eval.service;

import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.DocumentMeta;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public boolean hasPermission(AuthenticatedUser viewer, String code) {
        return viewer != null && viewer.permissions() != null && viewer.permissions().contains(code);
    }

    public boolean canView(AuthenticatedUser viewer, DocumentMeta doc) {
        if (viewer == null || doc == null) return false;
        if (hasPermission(viewer, "document:read:any")) return true;
        if (doc.getOwnerId() != null && doc.getOwnerId().equals(viewer.id())) return true;
        String vis = doc.getVisibility() == null ? "DEPARTMENT" : doc.getVisibility();
        switch (vis) {
            case "PUBLIC":
                return hasPermission(viewer, "document:read:public");
            case "DEPARTMENT":
                return hasPermission(viewer, "document:read:department")
                    && sameDepartment(viewer, doc);
            case "EXECUTIVE":
                return hasPermission(viewer, "document:read:executive");
            case "PRIVATE":
                return hasPermission(viewer, "document:read:private")
                    && doc.getOwnerId() != null && doc.getOwnerId().equals(viewer.id());
            default:
                return false;
        }
    }

    public boolean canManage(AuthenticatedUser viewer, DocumentMeta doc) {
        if (viewer == null || doc == null) return false;
        if (hasPermission(viewer, "document:manage:all")) return true;
        return hasPermission(viewer, "document:manage:own")
            && doc.getOwnerId() != null && doc.getOwnerId().equals(viewer.id());
    }

    private boolean sameDepartment(AuthenticatedUser viewer, DocumentMeta doc) {
        return viewer.department() != null && viewer.department().equals(doc.getOwnerDepartment());
    }
}
