package org.togetherjava.logwatcher.users;

import org.springframework.stereotype.Component;
import org.togetherjava.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Userroles;
import org.togetherjava.tjbot.db.generated.tables.pojos.Users;
import org.togetherjava.tjbot.db.generated.tables.records.UserrolesRecord;
import org.togetherjava.tjbot.db.generated.tables.records.UsersRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.togetherjava.tjbot.db.generated.tables.Users.USERS;

@Component
@SuppressWarnings("java:S1602") // Curly Braces are necessary here
public class UserRepositoryImpl implements IUserRepository {

    private final Database db;

    public UserRepositoryImpl(Database db) {
        this.db = db;
    }

    @Override
    public Users findByDiscordID(long discordID) {
        return this.db.readTransaction(ctx -> {
            return ctx.selectFrom(USERS)
                .where(USERS.DISCORDID.eq(discordID))
                .fetchOne(this::recordToRole);
        });
    }

    @Override
    public Users findByUsername(String username) {
        return this.db.readTransaction(ctx -> {
            return ctx.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne(this::recordToRole);
        });
    }

    @Override
    public List<Users> findAll() {
        return this.db.readTransaction(ctx -> {
            return ctx.selectFrom(USERS).fetch(this::recordToRole);
        });
    }

    @Override
    public int count() {
        return this.db.readTransaction(ctx -> {
            return ctx.fetchCount(USERS);
        });
    }

    @Override
    public void save(Users user) {
        this.db.writeTransaction(ctx -> {
            ctx.newRecord(USERS)
                .setDiscordid(user.getDiscordid())
                .setUsername(user.getUsername())
                .merge();
        });
    }

    @Override
    public void delete(Users user) {
        this.db.writeTransaction(ctx -> {
            ctx.deleteFrom(USERS).where(USERS.DISCORDID.eq(user.getDiscordid())).execute();
        });
    }

    @Override
    public Set<Role> fetchRolesForUser(Users user) {
        return new HashSet<>(this.db.readTransaction(ctx -> {
            return ctx.selectFrom(Userroles.USERROLES)
                .where(Userroles.USERROLES.USERID.eq(user.getDiscordid()))
                .fetch(this::recordToRole);
        }));
    }

    @Override
    public void saveRolesForUser(Users user, Set<Role> roles) {
        this.db.writeTransaction(ctx -> {
            ctx.deleteFrom(Userroles.USERROLES)
                .where(Userroles.USERROLES.USERID.eq(user.getDiscordid()))
                .execute();

            for (final Role role : roles) {
                ctx.newRecord(Userroles.USERROLES)
                    .setRoleid(role.getId())
                    .setUserid(user.getDiscordid())
                    .insert();
            }
        });
    }

    private Users recordToRole(UsersRecord usersRecord) {
        return new Users(usersRecord.getDiscordid(), usersRecord.getUsername());
    }

    private Role recordToRole(UserrolesRecord rolesRecord) {
        return Role.forID(rolesRecord.getRoleid());
    }
}
