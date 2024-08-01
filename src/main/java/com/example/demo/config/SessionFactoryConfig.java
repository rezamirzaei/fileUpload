package com.example.demo.config;

import org.hibernate.SessionFactory;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SessionFactoryConfig {

  private SessionFactory sessionFactory;

  @Bean
  public SessionFactory sessionFactory(HibernateEntityManagerFactory hemf){
    sessionFactory =  hemf.getSessionFactory();
    return sessionFactory;
  }

}