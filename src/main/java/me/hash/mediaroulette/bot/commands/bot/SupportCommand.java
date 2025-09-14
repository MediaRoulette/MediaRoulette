package me.hash.mediaroulette.bot.commands.bot;

import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;

public class SupportCommand extends BaseCommand {
    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final String SUPPORT_SERVER_URL = "https://discord.gg/Kr7qvutZ4N";

    @Override
    public CommandData getCommandData() {
        return Commands.slash("support", "🆘 Get help and join our support server")
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"support"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("support")) return;

        event.deferReply().queue();
        Bot.executor.execute(() -> {
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🆘 MediaRoulette Support")
                .setDescription("Need help with MediaRoulette? Join our support server!")
                .setColor(PRIMARY_COLOR)
                .addField("📋 What you can get help with:", 
                    "• Bot commands and features\n" +
                    "• Dictionary system setup\n" +
                    "• Settings configuration\n" +
                    "• Bug reports and feedback\n" +
                    "• Feature requests", false)
                .addField("🎯 Quick Links:", 
                    "• Use `/dictionary create` to make custom word lists\n" +
                    "• Use `/settings assign` to assign dictionaries to sources\n" +
                    "• Use `/chances` to configure source probabilities", false)
                .addField("👥 Community:", 
                    "Join our Discord server to connect with other users and get real-time support!", false)
                .setFooter("MediaRoulette Support", null)
                .setTimestamp(Instant.now());

            Button supportButton = Button.link(SUPPORT_SERVER_URL, "🔗 Join Support Server");
            
            event.getHook().sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(supportButton))
                .queue();
        });
    }
}