package com.linewell.dataelement.magic.runtime.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.ssssssss.magicapi.core.event.EventAction;
import org.ssssssss.magicapi.core.event.FileEvent;
import org.ssssssss.magicapi.core.event.MagicEvent;
import org.ssssssss.magicapi.core.model.MagicEntity;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.service.MagicResourceStorage;
import org.ssssssss.magicapi.core.service.impl.DefaultMagicResourceService;

/** Keeps existing mappings available while Magic reloads its database definitions. */
public final class StableMagicResourceService extends DefaultMagicResourceService {

    private final RefreshPublisher publisher;
    private final List<MagicResourceStorage<? extends MagicEntity>> storages;
    private final Runnable prepareRead;
    private final Runnable finishRead;

    public StableMagicResourceService(Resource resource, List<MagicResourceStorage<? extends MagicEntity>> storages,
                                      ApplicationEventPublisher publisher, Runnable prepareRead, Runnable finishRead) {
        this(resource, storages, new RefreshPublisher(publisher), prepareRead, finishRead);
    }

    private StableMagicResourceService(Resource resource, List<MagicResourceStorage<? extends MagicEntity>> storages,
                                       RefreshPublisher publisher, Runnable prepareRead, Runnable finishRead) {
        super(resource, storages, publisher);
        this.publisher = publisher;
        this.storages = List.copyOf(storages);
        this.prepareRead = prepareRead;
        this.finishRead = finishRead;
    }

    @Override
    public void refresh() {
        // Explicit refresh is synchronous. Editor saves and startup retain their ordinary events.
        var refresh = new Refresh();
        publisher.active.set(refresh);
        try {
            super.refresh();
            refresh.retirePreviousMappings(); // A completely empty tree emits no LOAD event.
        } finally {
            publisher.active.remove();
            finishRead.run();
        }
    }

    private Map<String, Definition> definitions() {
        var files = new HashMap<String, Definition>();
        for (var storage : storages) {
            // The first node is the editor's synthetic root, independent of the registered groups.
            var groups = tree(storage.folder()).flat();
            for (var group : groups.subList(1, groups.size())) {
                for (MagicEntity entity : listFiles(group.getId())) {
                    files.put(entity.getId(), new Definition(storage.folder(), mapping(storage, entity), entity));
                }
            }
        }
        return files;
    }

    private static <T extends MagicEntity> String mapping(MagicResourceStorage<T> storage, MagicEntity entity) {
        return storage.buildMappingKey(storage.magicClass().cast(entity));
    }

    private record Definition(String type, String key, MagicEntity entity) {
        boolean sameMapping(Definition other) {
            return other != null && type.equals(other.type) && Objects.equals(key, other.key);
        }
    }

    private final class Refresh {
        private Map<String, Definition> previous;

        boolean intercept(Object event) {
            if (event instanceof MagicEvent magic && magic.getAction() == EventAction.CLEAR) {
                // Magic emits CLEAR under its resource write lock, before invalidating the editor cache.
                previous = definitions();
                prepareRead.run();
                return true;
            }
            if (event instanceof FileEvent file && file.getAction() == EventAction.LOAD) {
                // The editor tree is complete before Magic publishes the first LOAD event.
                retirePreviousMappings();
            }
            return false;
        }

        void retirePreviousMappings() {
            if (previous == null) {
                return;
            }
            var oldFiles = previous;
            previous = null;
            var newFiles = definitions();
            oldFiles.forEach((id, old) -> {
                if (!old.sameMapping(newFiles.get(id))) {
                    publisher.delegate.publishEvent(new FileEvent(old.type(), EventAction.DELETE, old.entity()));
                }
            });
        }
    }

    private static final class RefreshPublisher implements ApplicationEventPublisher {
        private final ApplicationEventPublisher delegate;
        private final ThreadLocal<StableMagicResourceService.Refresh> active = new ThreadLocal<>();

        private RefreshPublisher(ApplicationEventPublisher delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publishEvent(Object event) {
            var refresh = active.get();
            if (refresh == null || !refresh.intercept(event)) {
                delegate.publishEvent(event);
            }
        }
    }
}
