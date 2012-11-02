/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.mihosoft.vrl.fxconnections;

/**
 *
 * @author Michael Hoffer <info@michaelhoffer.de>
 */
public interface Flow<T> {

    public FlowNode<T> newNode(T n);
    
    public Connection connect(FlowNode<T> s, FlowNode<T> r);
    
}