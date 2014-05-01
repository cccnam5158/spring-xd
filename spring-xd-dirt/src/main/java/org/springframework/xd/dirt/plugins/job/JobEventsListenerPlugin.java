/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.dirt.plugins.job;

import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.util.Assert;
import org.springframework.xd.dirt.integration.bus.MessageBus;
import org.springframework.xd.dirt.plugins.AbstractJobPlugin;
import org.springframework.xd.dirt.plugins.job.support.listener.XDJobListenerConstants;
import org.springframework.xd.module.core.Module;

/**
 * Plugin to enable the registration of out of the box job listeners.
 * 
 * @author Ilayaperumal Gopinathan
 * @since 1.0
 */
public class JobEventsListenerPlugin extends AbstractJobPlugin implements XDJobListenerConstants {

	private static final String JOB_TAP_CHANNEL_PREFIX = TAP_CHANNEL_PREFIX + JOB_CHANNEL_PREFIX;

	public JobEventsListenerPlugin(MessageBus messageBus) {
		super(messageBus);
	}

	@Override
	public void postProcessModule(Module module) {
		boolean disableListeners = true;
		Map<String, String> eventChannels = getEventListenerChannels(module);
		for (String publisherChannelName : eventChannels.keySet()) {
			MessageChannel eventChannel = module.getComponent(publisherChannelName, SubscribableChannel.class);
			if (eventChannel != null) {
				messageBus.bindPubSubProducer(eventChannels.get(publisherChannelName), eventChannel);
				disableListeners = false;
			}
		}
		// Bind aggregatedEvents channel if at least one of the event listeners channels is already bound.
		if (!disableListeners) {
			bindAggregatedEventsChannel(module);
		}
	}

	/**
	 * @param module
	 * @return the map containing the entries for the channels used by the job listeners with bean name of the channel
	 *         as the key and channel name as the value.
	 */
	private Map<String, String> getEventListenerChannels(Module module) {
		Map<String, String> eventListenerChannels = new HashMap<String, String>();
		String jobName = module.getDeploymentMetadata().getGroup();
		Assert.notNull(jobName, "Job name should not be null");
		eventListenerChannels.put(XD_JOB_EXECUTION_EVENTS_CHANNEL,
				getEventListenerChannelName(jobName, JOB_EXECUTION_EVENTS_SUFFIX));
		eventListenerChannels.put(XD_STEP_EXECUTION_EVENTS_CHANNEL,
				getEventListenerChannelName(jobName, STEP_EXECUTION_EVENTS_SUFFIX));
		eventListenerChannels.put(XD_CHUNK_EVENTS_CHANNEL, getEventListenerChannelName(jobName, CHUNK_EVENTS_SUFFIX));
		eventListenerChannels.put(XD_ITEM_EVENTS_CHANNEL, getEventListenerChannelName(jobName, ITEM_EVENTS_SUFFIX));
		eventListenerChannels.put(XD_SKIP_EVENTS_CHANNEL, getEventListenerChannelName(jobName, SKIP_EVENTS_SUFFIX));
		return eventListenerChannels;
	}


	private String getEventListenerChannelName(String jobName, String channelNameSuffix) {
		return String.format("%s%s.%s", JOB_TAP_CHANNEL_PREFIX, jobName, channelNameSuffix);
	}

	private String getEventListenerChannelName(String jobName) {
		return String.format("%s%s", JOB_TAP_CHANNEL_PREFIX, jobName);
	}

	private void bindAggregatedEventsChannel(Module module) {
		String jobName = module.getDeploymentMetadata().getGroup();
		MessageChannel aggEventsChannel = module.getComponent(XD_AGGREGATED_EVENTS_CHANNEL, SubscribableChannel.class);
		Assert.notNull(aggEventsChannel,
				"The pub/sub aggregatedEvents channel should be available in the module context.");
		messageBus.bindPubSubProducer(getEventListenerChannelName(jobName), aggEventsChannel);
	}

	@Override
	public void removeModule(Module module) {
		Map<String, String> eventListenerChannels = getEventListenerChannels(module);
		for (Map.Entry<String, String> channelEntry : eventListenerChannels.entrySet()) {
			messageBus.unbindProducers(channelEntry.getValue());
		}
		// unbind aggregatedEvents channel
		messageBus.unbindProducers(getEventListenerChannelName(module.getDeploymentMetadata().getGroup()));
	}
}
