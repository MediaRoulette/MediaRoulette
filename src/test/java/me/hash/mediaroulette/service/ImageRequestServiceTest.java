package me.hash.mediaroulette.service;

import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageRequestServiceTest {

    private ImageRequestService service;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private User user;

    @Mock
    private MessageChannelUnion channelUnion;

    @Mock
    private TextChannel textChannel;
    
    @Mock
    private InteractionHook hook;

    @Mock
    private WebhookMessageCreateAction action;

    @BeforeEach
    void setUp() {
        service = ImageRequestService.getInstance();
    }

    @Test
    void validateChannelAccess_PrivateChannel_UserNotNsfw_ReturnsFalse() {
        when(event.getChannelType()).thenReturn(ChannelType.PRIVATE);
        when(user.isNsfw()).thenReturn(false);
        
        // Mock chain for error handler
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(action);
        when(action.setEphemeral(true)).thenReturn(action);

        assertFalse(service.validateChannelAccess(event, user));
    }

    @Test
    void validateChannelAccess_TextChannel_UserNotNsfw_ChannelNsfw_SetsUserNsfw() {
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.asTextChannel()).thenReturn(textChannel);
        when(textChannel.isNSFW()).thenReturn(true);
        
        when(user.isNsfw()).thenReturn(false);

        assertTrue(service.validateChannelAccess(event, user));
        verify(user).setNsfw(true);
    }

    @Test
    void validateChannelAccess_TextChannel_UserNsfw_ChannelNotNsfw_ReturnsFalse() {
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.asTextChannel()).thenReturn(textChannel);
        when(textChannel.isNSFW()).thenReturn(false);
        
        when(user.isNsfw()).thenReturn(true);

        // Mock chain for error handler
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(action);
        when(action.setEphemeral(true)).thenReturn(action);

        assertFalse(service.validateChannelAccess(event, user));
    }
}
