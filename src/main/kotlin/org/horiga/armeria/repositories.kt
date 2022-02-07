package org.horiga.armeria

//import org.joda.time.LocalDateTime
import org.joda.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@Table("book")
data class Book(
    @Column("name")
    var name: String,
    @Column("isbn")
    var isbn: String,
    @Column("price")
    var price: Int,
    @Column("published_at")
    var publishedAt: LocalDateTime,
    @Column("updated_at")
    var updatedAt: LocalDateTime,
    @Id
    var id: Long? = null
)

@Repository
interface BookRepository: ReactiveCrudRepository<Book, Long?> {

    @Query("SELECT * FROM book WHERE name LIKE :name ORDER BY name")
    fun findByName(keyword: String): Flux<Book>

    @Query("SELECT * FROM book WHERE isbn = :isbn")
    fun findByIsbn(isbn: String): Mono<Book>
}