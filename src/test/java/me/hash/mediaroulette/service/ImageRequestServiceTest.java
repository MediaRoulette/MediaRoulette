package me.hash.mediaroulette.service;

import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.plugins.images.ImageSourceProvider;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private WebhookMessageEditAction<?> editAction;
    
    @Mock
    private ImageSourceProvider sfwProvider;
    
    @Mock
    private ImageSourceProvider nsfwProvider;
    
    @Mock
    private net.dv8tion.jda.api.entities.User discordUser;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = ImageRequestService.getInstance();
        
        // Setup common mocks for error handler
        when(event.getUser()).thenReturn(discordUser);
        when(discordUser.getEffectiveAvatarUrl()).thenReturn("https://example.com/avatar.png");
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginalComponents(any(net.dv8tion.jda.api.components.container.Container.class)))
                .thenReturn((WebhookMessageEditAction) editAction);
        when(editAction.useComponentsV2()).thenReturn((WebhookMessageEditAction) editAction);
    }
    
    @Test
    void validateChannelAccess_PrivateChannel_UserNotNsfw_ReturnsFalse() {
        when(nsfwProvider.isNsfw()).thenReturn(true);
        when(event.getChannelType()).thenReturn(ChannelType.PRIVATE);
        when(user.isNsfw()).thenReturn(false);

        assertFalse(service.validateChannelAccess(event, user, nsfwProvider));
    }

    @Test
    void validateChannelAccess_TextChannel_UserNotNsfw_ChannelNsfw_SetsUserNsfw() {
        when(nsfwProvider.isNsfw()).thenReturn(true);
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.asTextChannel()).thenReturn(textChannel);
        when(textChannel.isNSFW()).thenReturn(true);
        when(user.isNsfw()).thenReturn(false);

        assertTrue(service.validateChannelAccess(event, user, nsfwProvider));
        verify(user).setNsfw(true);
    }

    @Test
    void validateChannelAccess_TextChannel_UserNsfw_ChannelNotNsfw_ReturnsFalse() {
        when(nsfwProvider.isNsfw()).thenReturn(true);
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.asTextChannel()).thenReturn(textChannel);
        when(textChannel.isNSFW()).thenReturn(false);
        when(user.isNsfw()).thenReturn(true);

        assertFalse(service.validateChannelAccess(event, user, nsfwProvider));
    }
    
    @Test
    void validateChannelAccess_SfwProvider_NonNsfwChannel_ReturnsTrue() {
        // SFW providers (YouTube, Picsum, etc.) should work in any channel
        when(sfwProvider.isNsfw()).thenReturn(false);
        
        // No mocking of channel needed - SFW providers bypass channel check entirely
        assertTrue(service.validateChannelAccess(event, user, sfwProvider));
    }
    
    @Test
    void validateChannelAccess_NsfwProvider_NonNsfwChannel_ReturnsFalse() {
        // NSFW providers still require NSFW channel
        when(nsfwProvider.isNsfw()).thenReturn(true);
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.asTextChannel()).thenReturn(textChannel);
        when(textChannel.isNSFW()).thenReturn(false);
        when(user.isNsfw()).thenReturn(true);

        assertFalse(service.validateChannelAccess(event, user, nsfwProvider));
    }
    
    @Test
    void validateChannelAccess_NsfwProvider_NsfwChannel_ReturnsTrue() {
        // NSFW providers work in NSFW channels
        when(nsfwProvider.isNsfw()).thenReturn(true);
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel()).thenReturn(channelUnion);
        when(channelUnion.asTextChannel()).thenReturn(textChannel);
        when(textChannel.isNSFW()).thenReturn(true);
        when(user.isNsfw()).thenReturn(true);

        assertTrue(service.validateChannelAccess(event, user, nsfwProvider));
    }
}
