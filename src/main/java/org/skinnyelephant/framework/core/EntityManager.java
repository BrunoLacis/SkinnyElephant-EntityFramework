/*
 * Copyright 2012  Kristaps Kohs<kristaps.kohs@gmail.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.skinnyelephant.framework.core;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.skinnyelephant.framework.systems.EntitySystem;
import org.skinnyelephant.framework.util.EntityIdGenerator;
import org.skinnyelephant.framework.util.EntityIdGeneratorImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity Manager class for managing entites in application.
 *
 * @author Kristaps Kohs
 */
public class EntityManager implements Manager {
    /**
     * Flag if manager is initialized*
     */
    private boolean initialized;
    /**
     * Cache containing Immutable Set of entities for each Entity System.
     */
    private final Multimap<EntitySystem, Entity> systemCache = HashMultimap.create();
    /**
     * Map of string id referenced entities.
     */
    private final Map<String, Entity> referencedEntities = new HashMap<String, Entity>();
    /**
     * Map containing all present entities.
     */
    private final Map<Long, Entity> entityMap = new HashMap<Long, Entity>();
    /**
     * Support class for generating entity id and reusing them.
     */
    private final EntityIdGenerator entityIdGenerator = new EntityIdGeneratorImpl();
    /**
     * Reference to Core class.
     */
    private final Core core;

    /**
     * Constructor for creating Entity manager
     *
     * @param core reference to {@link Core} class
     */
    protected EntityManager(final Core core) {
        this.core = core;
    }

    @Override
    public void initialize() {
        if (!initialized) {
            initialized = true;
        } else {
            throw new IllegalStateException("Manager already initialized");
        }
    }

    @Override
    public void dispose() {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized.");
        }
        systemCache.clear();
        referencedEntities.clear();
        for (Entity e : entityMap.values()) {
            e.dispose();
        }
        entityMap.clear();
        entityIdGenerator.reset();
        initialized = false;
    }

    /**
     * Cleans up all entities.
     */
    public void cleanUp() {
        for (Entity e : entityMap.values()) {
            removeEntity(e);
        }
    }

    /**
     * <p>Method for registering entity to manager.</p>
     * <p>If entity contains String reference, entity is registered into referenced map. </p>
     * <p>Unique EntityID is set when its registered into system.</p>
     *
     * @param e Entity to register.
     */
    protected final void addEntity(final Entity e) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        if (e.getReference() != null) {
            referencedEntities.put(e.getReference(), e);
        }
        e.setEntityId(entityIdGenerator.getId());
        entityMap.put(e.getEntityId(), e);
    }

    /**
     * Returns Entity by its reference.
     *
     * @param reference unique entity reference.
     * @return entity with the given reference
     * @throws IllegalArgumentException if entity does not exist.
     */
    public Entity getEntity(final String reference) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        if (!referencedEntities.containsKey(reference)) {
            throw new IllegalArgumentException("Entity with reference " + reference + " not registered.");
        }
        return referencedEntities.get(reference);
    }

    /**
     * <p>Method for getting {@link ImmutableSet} of {@link Entity} for given {@link EntitySystem}</p>
     * <p>If system cache contains Set of entities for this system then its returned otherwise,
     * all of the entities are checked for components required to this system and added to set.</p>
     *
     * @param system System for which entities are requested.
     * @return Set of entities for given system.
     */
    public final ImmutableSet<Entity> getEntitiesForSystem(final EntitySystem system) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        ImmutableSet<Entity> set = ImmutableSet.copyOf(systemCache.get(system));
        if (set != null && !set.isEmpty()) return set;

        for (Entity e : entityMap.values()) {
            long bit = (e.getComponentsIds() & system.getUsedComponents());
            if (bit == system.getUsedComponents() && bit != 0) {
                systemCache.put(system, e);
            }
        }


        return ImmutableSet.copyOf(systemCache.get(system));
    }

    /**
     * Invalidates cache for all systems which contains given component id.
     *
     * @param e Entity to add to system cache.
     */
    protected final void addToCache(Entity e) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        for (EntitySystem system : core.getSystems()) {
            if ((system.getUsedComponents() & e.getComponentsIds()) == system.getUsedComponents()) {
                systemCache.put(system, e);
            }
        }
    }

    /**
     * Removes entity from system cache.
     *
     * @param e to be removed.
     */
    protected void removeFromCache(Entity e) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        for (EntitySystem system : core.getSystems()) {
            if ((system.getUsedComponents() & e.getComponentsIds()) == system.getUsedComponents()) {
                systemCache.remove(system, e);
            }
        }
    }

    /**
     * Removes entity from system, invalidates cache for all entity components and disposes of entity components.
     *
     * @param e Entity to remove.
     */
    protected void removeEntity(final Entity e) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        removeFromCache(e);
        entityIdGenerator.removeId(e.getEntityId());
        if (e.getReference() != null) {
            referencedEntities.remove(e.getReference());
        }
        entityMap.remove(e.getEntityId());
        e.dispose();
    }

    /**
     * Returns entity by its ID.
     *
     * @param id Entity ID
     * @return Entity by given ID
     * @throws IllegalArgumentException if entity does not exist.
     */
    public Entity getEntity(final Long id) {
        if (!initialized) {
            throw new IllegalStateException("Manager not initialized");
        }
        if (!entityMap.containsKey(id)) {
            return null;
        }
        return entityMap.get(id);
    }

    /**
     * Returns all entities in current framework.
     *
     * @return immutable set of entities.
     */
    public final ImmutableSet<Entity> getAllEntities() {
        return ImmutableSet.copyOf(entityMap.values());
    }

    /**
     * Returns all entities with given component.
     *
     * @param componentType component class.
     * @return Immutable set of entities with provided component type.
     */
    public final ImmutableSet<Entity> getEntitiesByComponent(Class<?> componentType) {
        long id = core.getComponentId(componentType);
        if (id == 0) return null;
        List<Entity> list = new ArrayList<Entity>();
        for (Entity e : entityMap.values()) {
            if ((e.getComponentsIds() & id) != 0) {
                list.add(e);
            }
        }
        return ImmutableSet.copyOf(list);

    }
}

