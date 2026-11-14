package com.github.gcolin.platform.plugin;

import com.github.gcolin.player.IPlayer;
import java.util.List;

/** Extra players proposed to a user on registration pages (e.g. players they created). */
public interface RegistrationPlayerSource {

    List<? extends IPlayer> playersFor(String userEmail);
}
