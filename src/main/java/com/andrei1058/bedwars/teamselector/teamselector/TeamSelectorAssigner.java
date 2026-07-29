package com.andrei1058.bedwars.teamselector.teamselector;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.ITeamAssigner;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.events.gameplay.TeamAssignEvent;
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
        ArenaPreferences registeredPreference = TeamManager.getInstance().getArena(arena);

        List<PlayerGroup> playerGroups = new LinkedList<>();
        for (ITeam preference : registeredPreference.getSelections().values().stream().distinct().collect(Collectors.toList())) {
            PlayerGroup group = new PlayerGroup(arena, preference);
            for (Player player : registeredPreference.getMembers(preference)) {
                group.addPlayer(player);
                players.remove(player);
            }
            playerGroups.add(group);
        }

        Collections.sort(playerGroups);
        for (PlayerGroup group : playerGroups) {
            if (group.getMembers().isEmpty()) continue;
            ITeam targetTeam = group.getPreference();
            if (targetTeam != null && targetTeam.getMembers().size() + group.getMembers().size() <= arena.getMaxInTeam()) {
                for (Player player : group.getMembers()) {
                    targetTeam.addPlayers(player);
                    callTeamAssignEvent(player, targetTeam, arena);
                }
                if (targetTeam.getMembers().size() == arena.getMaxInTeam()) {
                    teams.remove(targetTeam);
                }
            }
        }

        // 打乱玩家顺序，避免原来的顺序影响分配
        Collections.shuffle(players);

        // 根据人数选择分配策略
        if (playerCount == 2 || playerCount == 4 || playerCount == 6 || playerCount == 8) {
            // 平均分配到两个指定队伍
            assignToTwoTeams(arena, players, teams);
        } else {
            // 平均分配到所有队伍
            assignEvenlyToAllTeams(arena, players, teams);
        }
    }

    /**
     * 将玩家平均分配到两个队伍（优先红绿/红蓝，否则随机两个）
     */
    private void assignToTwoTeams(IArena arena, List<Player> players, List<ITeam> teams) {
        List<ITeam> targetTeams = findTwoTargetTeams(teams);
        if (targetTeams.size() < 2) return;

        targetTeams.subList(2, targetTeams.size()).clear();

        int max = arena.getMaxInTeam();
        // 只保留未满的队伍
        List<ITeam> available = new ArrayList<>();
        for (ITeam team : targetTeams) {
            if (team.getMembers().size() < max) {
                available.add(team);
            }
        }
        if (available.isEmpty()) return;
        for (Player player : players) {
            // 从可用队伍中选出当前人数最少的（如果多人最少，取第一个）
            ITeam chosen = null;
            int minSize = Integer.MAX_VALUE;
            for (ITeam team : available) {
                int size = team.getMembers().size();
                if (size < minSize && size < max) {
                    minSize = size;
                    chosen = team;
                }
            }
            // 如果没有可选队伍，则退出
            if (chosen == null) break;

            chosen.addPlayers(player);
            callTeamAssignEvent(player, chosen, arena);
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

        int max = arena.getMaxInTeam();
        // 只保留未满的队伍
        List<ITeam> available = new ArrayList<>();
        for (ITeam team : teams) {
            if (team.getMembers().size() < max) {
                available.add(team);
            }
        }
        if (available.isEmpty()) return;
        for (Player player : players) {
            // 从可用队伍中选出当前人数最少的（如果多人最少，取第一个）
            ITeam chosen = null;
            int minSize = Integer.MAX_VALUE;
            for (ITeam team : available) {
                int size = team.getMembers().size();
                if (size < minSize && size < max) {
                    minSize = size;
                    chosen = team;
                }
            }
            // 如果没有可选队伍，则退出
            if (chosen == null) break;

            chosen.addPlayers(player);
            callTeamAssignEvent(player, chosen, arena);
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