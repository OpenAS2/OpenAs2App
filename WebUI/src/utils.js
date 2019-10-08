import axios from 'axios';
import store from './store';

const Utils = {
    URLEncode:function(element,key,list){
        list = list || [];
        if(typeof(element)=='object'){
          for (var idx in element)
          Utils.URLEncode(element[idx],key?key+'['+idx+']':idx,list);
        } else {
          list.push(key+'='+encodeURIComponent(element));
        }
        return list.join('&');
      },
    Crud: {
        getList: async function(resource) {
            var url =store.state.server +  `/${resource}/list`;
            try {
                var response= await axios.get(url,{ auth: { username: store.state.username, password: store.state.password }});
                if(response.data.type === 'OK') {
                    return response.data.results;
                    
                }else{
                    throw response.data.result;
                }
            }catch(e) {
                console.log(e);
                throw(`Error getting ${resource} list:\n${e}`);
            }
        },
        getObject: async function(resource, index, empty) {
            console.log(`Loading Object ${index} from ${resource}`);
            var emptyObject = Object.assign({}, empty , {"_id":null });
            if(index === 'new') return emptyObject;
            var list = await this.getList(resource);
            console.log(list);
            var item =list[index];
            var url=store.state.server +  `/${resource}/view/${item}`;
            console.log(url);
            try {
                var response= await axios.get(url ,{  auth: { username: store.state.username, password: store.state.password } });
                if(response.data.type == 'OK') {
                    var results=response.data.results[0];
                    results._id = index;
                    console.log(results);
                    return results;
                }else{
                    throw response.data.result;
                }
            }catch(e) {
                console.log(e);
                throw(`Error loading Object ${index}:\n${e}`);
            }
        },
        saveObject: function(resource, data) {
            console.log('Saving',data);
            var old_id=data._id;
            delete data._id;
            if(old_id !== null) {
                return this.deleteObject(resource,old_id).then( () => this.createObject(resource,data));
            }else{
                return this.createObject(resource,data);
            }
            
        },
        importCertificate: async function(data) {
            console.log('Creating',data);
            var url=store.state.server +  `/cert/importbystream/${data.alias}` ;
            try {
                var form = '';
                if(data._prefix) {
                    form = data._prefix + '&';
                }
                delete data._prefix;
                form += Utils.URLEncode(data);
                console.log('Posting Data', form);
                var response = await axios.post(url,form,{  auth: { username: store.state.username, password: store.state.password } });
                if(response.data.type === 'OK') {
                    return true;
                }else{
                    throw response.data.result;
                }
            }catch(e) {
                console.log(e,url);
                throw(`Error Creating Object:\n${e}`);
            }
        },
        createObject: async function(resource,data) {
            console.log('Creating',data);
            var url=store.state.server +  `/${resource}/add/${data.name}` ;
            try {
                var form = '';
                if(data._prefix) {
                    form = data._prefix + '&';
                }
                delete data._prefix;
                form += Utils.URLEncode(data);
                console.log('Posting Form', form);
                var response = await axios.post(url,form,{  auth: { username: store.state.username, password: store.state.password } });
                if(response.data.type === 'OK') {
                    return true;
                }else{
                    throw response.data.result;
                }
            }catch(e) {
                console.log(e,url);
                throw(`Error Creating Object:\n${e}`);
            }
        },
        deleteObject: async function(resource,index) {
            console.log('Deleting',index);
            var list = await this.getList(resource);
            var item =list[index];
            var url=store.state.server +  `/${resource}/delete/${item}`;
            try {
                var response = await axios.get(url,{  auth: { username: store.state.username, password: store.state.password } });
                if(response.data.type === 'OK') {
                    //var results=response.data.result;
                    return true;
                }else{
                    throw response.data.result;
                }
            }catch(e) {
                console.log(e,url);
                throw(`Error deleting Object ${index}:\n${e}`);
            }
        }
    }
};
export default Utils;