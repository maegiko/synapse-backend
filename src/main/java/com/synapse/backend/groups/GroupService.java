package com.synapse.backend.groups;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.synapse.backend.groups.dto.CreateGroupRequest;
import com.synapse.backend.groups.dto.GroupDetailResponse;
import com.synapse.backend.groups.dto.GroupListResponse;
import com.synapse.backend.groups.dto.GroupResponse;
import com.synapse.backend.groups.dto.UpdateGroupRequest;
import com.synapse.backend.groups.exceptions.InvalidGroupDetailsException;

@Service
public class GroupService {
    private final GroupPersistenceService persistenceService;

    public GroupService(GroupPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Creates a new empty group for the currently authenticated user.
     *
     * <p>The request arrives with its name and description trimmed. New groups hold no content;
     * notes, decks, and quizzes are added with the membership routes.</p>
     *
     * @param userId the id of the currently authenticated user.
     * @param req the validated group name and optional description.
     * @return the newly created group.
     */
    public GroupResponse createGroup(Long userId, CreateGroupRequest req) {
        String description = req.description();

        return persistenceService.createGroup(
            userId,
            req.name(),
            description == null || description.isBlank() ? null : description
        );
    }

    /**
     * Returns a page of groups owned by the currently authenticated user, optionally filtered by name.
     *
     * @param userId the id of the currently authenticated user.
     * @param query an optional case-insensitive partial name search, or null/blank for no search.
     * @param pageable the page to return.
     * @return the requested page of the user's groups, newest first, each with its content counts.
     */
    public GroupListResponse getAllGroups(Long userId, String query, Pageable pageable) {
        return persistenceService.getAllGroups(userId, query, pageable);
    }

    /**
     * Returns a single group owned by the currently authenticated user.
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     * @return the group and lightweight lists of its notes, decks, and quizzes.
     */
    public GroupDetailResponse getGroup(String groupId, Long userId) {
        return persistenceService.getGroup(groupId, userId);
    }

    /**
     * Updates the name and/or description of a group owned by the currently authenticated user.
     *
     * <p>Only the supplied fields are changed. The request arrives with its name and description
     * trimmed, matching group creation. Supplying a blank description clears it.</p>
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     * @param req the validated details to update, with at least one field supplied.
     * @return the updated group.
     * @throws InvalidGroupDetailsException if no field is supplied or the supplied name is blank.
     */
    public GroupResponse updateGroup(String groupId, Long userId, UpdateGroupRequest req) {
        String name = req.name();
        String description = req.description();

        if (name == null && description == null)
            throw new InvalidGroupDetailsException("At least one of name or description must be supplied.");

        if (name != null && name.isBlank())
            throw new InvalidGroupDetailsException("name: must not be blank");

        return persistenceService.updateGroup(groupId, userId, name, description);
    }

    /**
     * Deletes a group owned by the currently authenticated user.
     *
     * <p>The group's content is kept and becomes ungrouped.</p>
     *
     * @param groupId the public id of the group.
     * @param userId the id of the currently authenticated user.
     */
    public void deleteGroup(String groupId, Long userId) {
        persistenceService.deleteGroup(groupId, userId);
    }

}
