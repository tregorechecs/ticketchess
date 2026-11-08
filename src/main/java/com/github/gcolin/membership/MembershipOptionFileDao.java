package com.github.gcolin.membership;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MembershipOptionFileDao extends AbstractDao<MembershipOptionFile> {

    public MembershipOptionFileDao() {
        super(MembershipOptionFile.class, "membership_option_file");
    }

    @Override
    protected List<MembershipOptionFile> select(String sql, Object... params) {
        List<MembershipOptionFile> list = selectWith(
                (file, rs) -> {
                    Integer optionId = intOrNull(rs, "membership_option_id");
                    if (optionId != null) {
                        MembershipOption option = new MembershipOption();
                        option.setId(optionId);
                        file.setMembershipOption(option);
                    }
                },
                sql,
                params);
        Set<Integer> optionIds = new LinkedHashSet<>();
        for (MembershipOptionFile file : list) {
            if (file.getMembershipOption() != null) {
                optionIds.add(file.getMembershipOption().getId());
            }
        }
        Map<Integer, MembershipOption> options = new MembershipOptionDao().findByIds(optionIds);
        for (MembershipOptionFile file : list) {
            if (file.getMembershipOption() != null) {
                MembershipOption option = options.get(file.getMembershipOption().getId());
                if (option != null) {
                    file.setMembershipOption(option);
                }
            }
        }
        return list;
    }

    public List<MembershipOptionFile> findByOptionId(Integer optionId) {
        if (optionId == null) {
            return Collections.emptyList();
        }
        return select(
                "SELECT * FROM membership_option_file WHERE membership_option_id = ?"
                        + " ORDER BY created_at DESC, id DESC",
                optionId);
    }

    public List<MembershipOptionFile> findByOptionIds(List<Integer> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return select(
                "SELECT * FROM membership_option_file WHERE membership_option_id IN ("
                        + placeholders(optionIds)
                        + ") ORDER BY membership_option_id, created_at DESC, id DESC",
                optionIds.toArray());
    }

    public MembershipOptionFile findWithOption(Integer id) {
        if (id == null) {
            return null;
        }
        return find(id);
    }

    @Override
    public void persist(MembershipOptionFile file) {
        if (file.getId() == null) {
            touchCreated(file);
            int id = Db.insertReturningInt(
                    "INSERT INTO membership_option_file (created_at, updated_at, membership_option_id, original_name,"
                            + " stored_name, content_type, size_bytes, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    file.getCreatedAt(),
                    file.getUpdatedAt(),
                    Db.fkId(file.getMembershipOption()),
                    file.getOriginalName(),
                    file.getStoredName(),
                    file.getContentType(),
                    file.getSizeBytes(),
                    file.getUploadedBy());
            file.setId(id);
        } else {
            touchUpdated(file);
            Db.update(
                    "UPDATE membership_option_file SET updated_at = ?, membership_option_id = ?, original_name = ?,"
                            + " stored_name = ?, content_type = ?, size_bytes = ?, uploaded_by = ? WHERE id = ?",
                    file.getUpdatedAt(),
                    Db.fkId(file.getMembershipOption()),
                    file.getOriginalName(),
                    file.getStoredName(),
                    file.getContentType(),
                    file.getSizeBytes(),
                    file.getUploadedBy(),
                    file.getId());
        }
    }

    @Override
    public MembershipOptionFile merge(MembershipOptionFile file) {
        persist(file);
        return file;
    }
}
