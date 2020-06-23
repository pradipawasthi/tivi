/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.data.repositories.shows

import app.tivi.data.daos.TiviShowDao
import app.tivi.data.entities.Success
import app.tivi.data.entities.TiviShow
import app.tivi.data.toFetchResult
import com.dropbox.android.external.store4.SourceOfTruth
import com.dropbox.android.external.store4.Store
import com.dropbox.android.external.store4.StoreBuilder
import com.dropbox.android.external.store4.nonFlowFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import javax.inject.Singleton

typealias ShowStore = Store<Long, TiviShow>

@InstallIn(ApplicationComponent::class)
@Module
object ShowStoreModule {
    @Provides
    @Singleton
    fun provideShowStore(
        showDao: TiviShowDao,
        lastRequestStore: ShowLastRequestStore,
        traktShowDataSource: TraktShowDataSource
    ): ShowStore = StoreBuilder.from(
        fetcher = nonFlowFetcher { id: Long ->
            traktShowDataSource.getShow(showDao.getShowWithIdOrThrow(id))
                .also {
                    if (it is Success<*>) {
                        lastRequestStore.updateLastRequest(id)
                    }
                }
                .toFetchResult()
        },
        sourceOfTruth = SourceOfTruth.from(
            reader = showDao::getShowWithIdFlow,
            writer = { id, response ->
                showDao.withTransaction {
                    showDao.insertOrUpdate(
                        mergeShows(local = showDao.getShowWithIdOrThrow(id), trakt = response)
                    )
                }
            },
            delete = showDao::delete,
            deleteAll = showDao::deleteAll
        )
    ).build()
}
