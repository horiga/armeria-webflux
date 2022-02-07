package org.horiga.armeria.service

import org.horiga.armeria.Book
import org.horiga.armeria.BookRepository
import org.joda.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
@Transactional
class BookService(val bookRepository: BookRepository) {

    companion object {
        fun convertPublishedAt(publishedAt: String): LocalDateTime {
            return LocalDateTime.parse(publishedAt + "T10:00:00")
        }
    }

    fun findById(id: Long) = bookRepository.findById(id)

    fun findAll() = bookRepository.findAll()

    fun searchByName(name: String) = bookRepository.findByName("%$name%")

    fun findByIsbn(isbn: String) = bookRepository.findByIsbn(isbn)

    fun add(name: String, isbn: String, price: Int, publishedAt: String): Mono<Book> =
        bookRepository.save(
            Book(name, isbn, price, convertPublishedAt(publishedAt), LocalDateTime.now())
        ).flatMap { entity ->
            if (isbn == "999")
                return@flatMap Mono.error(IllegalStateException("Thrown illegal state error!!"))
            Mono.just(entity)
        }

    fun delete(id: Long) = bookRepository.deleteById(id)
}