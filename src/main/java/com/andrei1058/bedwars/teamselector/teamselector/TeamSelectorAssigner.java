package com.andrei1058.bedwars.teamselector.teamselector;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.ITeamAssigner;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.events.gameplay.TeamAssignEvent;
import com.andrei1058.bedwars.teamselector.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class TeamSelectorAssigner implements ITeamAssigner {

    @Override
    public void assignTeams(@NotNull IArena arena) {
        List<Player> players = new ArrayList<>(arena.getPlayers());
        List<ITeam> teams = new ArrayList<>(arena.getTeams());
        int playerCount = players.size();
        int teamCount = teams.size();

        if (playerCount == 0 || teamCount == 0) {
            return;
        }

        // 打乱玩家顺序，避免原来的顺序影响分配
        Collections.shuffle(players);

        // 根据人数选择分配策略
        if (playerCount == 2 || playerCount == 4 || playerCount == 6 || playerCount == 8) {
            // 平均分配到两个指定队伍
            assignToTwoTeams(arena, players, teams);
        } else if (playerCount == 3 || playerCount == 5 || playerCount == 7) {
            // 平均分配到所有队伍
            assignEvenlyToAllTeams(arena, players, teams);
        } else {
            // 9 人及以上：完全随机分配
            assignRandomly(arena, players, teams);
        }
    }

    /**
     * 将玩家平均分配到两个队伍（优先红绿/红蓝，否则随机两个）
     */
    private void assignToTwoTeams(IArena arena, List<Player> players, List<ITeam> teams) {
        List<ITeam> targetTeams = findTwoTargetTeams(teams);
        if (targetTeams.size() < 2) return;

        ITeam teamA = targetTeams.get(0);
        ITeam teamB = targetTeams.get(1);
        int half = players.size() / 2;

        // 前一半给 teamA，后一半给 teamB
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            ITeam target = (i < half) ? teamA : teamB;
            target.addPlayers(p);
            callTeamAssignEvent(p, target, arena);
        }
    }

    /**
     * 从队伍列表中找到两个目标队伍（优先红/绿，其次红/蓝，否则随机）
     */
    private List<ITeam> findTwoTargetTeams(List<ITeam> teams) {
        ITeam redTeam = null;
        ITeam greenTeam = null;

        for (ITeam team : teams) {
            TeamColor color = team.getColor();
            if (color == TeamColor.RED) {
                redTeam = team;
            } else if (color == TeamColor.GREEN) {
                greenTeam = team;
            }
        }

        // 优先返回红绿队伍
        if (redTeam != null && greenTeam != null) {
            return Arrays.asList(redTeam, greenTeam);
        }

        // 否则随机返回两个不同的队伍（复制列表避免影响原顺序）
        List<ITeam> shuffled = new ArrayList<>(teams);
        Collections.shuffle(shuffled);
        return Arrays.asList(shuffled.get(0), shuffled.get(1));
    }

    /**
     * 将玩家平均分配到所有队伍（人数差不超过 1）
     */
    private void assignEvenlyToAllTeams(IArena arena, List<Player> players, List<ITeam> teams) {
        int total = players.size();
        int teamCount = teams.size();
        int base = total / teamCount;          // 每个队伍至少多少人
        int remainder = total % teamCount;     // 前 remainder 个队伍多一人

        int playerIndex = 0;
        for (int i = 0; i < teamCount; i++) {
            ITeam team = teams.get(i);
            int membersToAdd = base + (i < remainder ? 1 : 0);
            for (int j = 0; j < membersToAdd && playerIndex < total; j++) {
                Player p = players.get(playerIndex++);
                team.addPlayers(p);
                callTeamAssignEvent(p, team, arena);
            }
        }
    }

    /**
     * 完全随机分配（不考虑平均，只遵守队伍人数上限）
     */
    private void assignRandomly(IArena arena, List<Player> players, List<ITeam> teams) {
        int maxInTeam = arena.getMaxInTeam();
        Random random = new Random();

        for (Player p : players) {
            // 随机选一个未满的队伍
            List<ITeam> availableTeams = teams.stream()
                    .filter(t -> t.getMembers().size() < maxInTeam)
                    .collect(Collectors.toList());
            if (availableTeams.isEmpty()) {
                // 所有队伍都满了，理论上不会发生，但保底处理
                break;
            }
            ITeam target = availableTeams.get(random.nextInt(availableTeams.size()));
            target.addPlayers(p);
            callTeamAssignEvent(p, target, arena);
        }
    }

    /**
     * 触发队伍分配事件
     */
    private void callTeamAssignEvent(Player player, ITeam team, IArena arena) {
        TeamAssignEvent event = new TeamAssignEvent(player, team, arena);
        Bukkit.getPluginManager().callEvent(event);
    }
}