package me.hash.mediaroulette.bot.commands.admin;

import java.io.IOException;

import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.errorHandler;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.content.factory.MediaServiceFactory;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import net.dv8tion.jda.api.Permission;
import me.hash.mediaroulette.bot.Bot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class ChannelNuke extends BaseCommand {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("nuke", "Nukes and throws white fluids on old channel")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    @CommandCooldown(value = 3, commands = {"nuke"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("nuke"))
            return;

        event.deferReply().queue();
        Bot.executor.execute(() -> {
            if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
                errorHandler.sendErrorEmbed(event, "Sorry dude...", "You do not have the Manage Channel permission.");
                return;
            }

            if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
                errorHandler.sendErrorEmbed(event, "Sorry dude...", "I do not have the Manage Channel permission.");
                return;
            }

            TextChannel oldChannel = event.getChannel().asTextChannel();
            int position = oldChannel.getPosition();

            TextChannel newChannel = oldChannel.createCopy().setPosition(position).complete();

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Channel Nuked");
            try {
                embedBuilder.setImage(new MediaServiceFactory().createTenorProvider().getRandomMedia("nuke").toMap().get("image"));
            } catch (IOException ignored) {
            } catch (HttpClientWrapper.RateLimitException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            newChannel.sendMessageEmbeds(embedBuilder.build()).queue();

            oldChannel.delete().queue();
        });
    }

}
