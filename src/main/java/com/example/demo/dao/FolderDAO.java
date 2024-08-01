package com.example.demo.dao;

import com.example.demo.model.Folder;
import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by Reza-PC on 7/24/2017.
 */
@Repository
public class FolderDAO {
    @Autowired
    SessionFactory sessionFactory;

    public void create(Folder folder){
        sessionFactory.getCurrentSession().save(folder);
    }
    public void update(Folder folder){
        sessionFactory.getCurrentSession().update(folder);
    }
    public void delete(Folder folder){
        sessionFactory.getCurrentSession().delete(folder);
    }
    public Folder load(Long id){
        return sessionFactory.getCurrentSession().load(Folder.class,id);
    }
    public Folder LoadByName(String name){
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Folder.class);
        criteria.add(Restrictions.eq("name", name));
        List<Folder> list = criteria.list();
        if (list == null || list.size() == 0) {
            return null;
        }
        return list.get(0);
    }
    public List loadAll(){
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Folder.class);
        List<Folder> list = criteria.list();
        return list;
    }
    public List loadAllName(){
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Folder.class,"f");
        criteria.setProjection(Projections.property("f.name"));
        return criteria.list();
    }

}
