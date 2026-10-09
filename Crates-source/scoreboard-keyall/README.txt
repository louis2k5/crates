Key All in ScoreboardPL
=======================
ScoreboardPL-1.5.0-KeyAll.jar is the ScoreboardPL-1.5.0 jar with these changes:

1. New class de.maximilian.scoreboardpl.hook.KeyAllHook (KeyAllHook.java in this folder).
2. ScoreboardManager, two lines:
     updateScoreboard():   List<String> lines = KeyAllHook.lines(this.plugin, this.plugin.getConfig().getStringList("scoreboard.lines"));
     parsePlaceholders():  line = KeyAllHook.apply(this.plugin, line);     // directly before PlaceholderAPIHook.parse(player, line)
3. config.yml: the "key-all:" section and the placeholder description.
4. plugin.yml: "Crates" added to softdepend, version 1.5.0-keyall.

Everything else in the jar is byte for byte the original.
