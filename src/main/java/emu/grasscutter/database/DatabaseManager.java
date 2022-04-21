package emu.grasscutter.database;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;

import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.GenshinPlayer;
import emu.grasscutter.game.avatar.GenshinAvatar;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.inventory.GenshinItem;

public final class DatabaseManager {
	private static MongoClient mongoClient;
	private static Datastore datastore;
	
	private static final Class<?>[] mappedClasses = new Class<?>[] {
		DatabaseCounter.class, Account.class, GenshinPlayer.class, GenshinAvatar.class, GenshinItem.class, Friendship.class
	};
    
    public static Datastore getDatastore() {
    	return datastore;
    }
    
    public static MongoDatabase getDatabase() {
    	return getDatastore().getDatabase();
    }
	
	public static void initialize() {
		// Initialize
		mongoClient = MongoClients.create(Grasscutter.getConfig().DatabaseUrl);
		
		// Set mapper options.
		MapperOptions mapperOptions = MapperOptions.builder()
				.storeEmpties(true).storeNulls(false).build();
		// Create data store.
		datastore = Morphia.createDatastore(mongoClient, Grasscutter.getConfig().DatabaseCollection, mapperOptions);
		// Map classes.
		datastore.getMapper().map(mappedClasses);
		
		// Ensure indexes
		try {
			datastore.ensureIndexes();
		} catch (MongoCommandException exception) {
			Grasscutter.getLogger().info("Mongo index error: ", exception);
			// Duplicate index error
			if (exception.getCode() == 85) {
				// Drop all indexes and re add them
				MongoIterable<String> collections = datastore.getDatabase().listCollectionNames();
				for (String name : collections) {
					datastore.getDatabase().getCollection(name).dropIndexes();
				}
				// Add back indexes
				datastore.ensureIndexes();
			}
		}
	}

	public static synchronized int getNextId(Class<?> c) {
		DatabaseCounter counter = getDatastore().find(DatabaseCounter.class).filter(Filters.eq("_id", c.getName())).first();
		if (counter == null) {
			counter = new DatabaseCounter(c.getSimpleName());
		}
		try {
			return counter.getNextId();
		} finally {
			getDatastore().save(counter);
		}
	}

	public static synchronized int getNextId(Object o) {
		return getNextId(o.getClass());
	}
}