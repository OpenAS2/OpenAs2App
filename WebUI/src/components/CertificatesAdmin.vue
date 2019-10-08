<template>
    <div id="certificates">
        <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
        <h1 class="h2">Certificates</h1>
          <div class="btn-toolbar mb-2 mb-md-0">
            <div class="btn-group mr-2">
              <button type="button"  @click="$router.push({ name: 'CertificateEditor',params: { id: 'new'} })" class="btn btn-sm btn-outline-secondary">New</button>
              
            </div>
          </div>
        </div>
      <certificate-editor v-if="$route.params.id" 
            title="Certificate Editor"
            :schema="schema"
            :model="getObject($route.params.id)"
            @commit="saveObject" 
            @delete="(id)=>this.deleteObject(id)" 
            @revert="revert">
            
        </certificate-editor>
        <div v-else>
            <object-list :src="getList"></object-list>
        </div>
    </div>
</template>
<script>
import ObjectList from './ObjectList';
import CertificateEditor from './CertificateEditor';
import Utils from '../utils';
export default {
  data: function() {
        return {
            schema: { 
                fields: [
                  {
                    type: 'text',
                    name: 'alias',
                    label: 'Alias Name',
                    placeholder: "Certificate's Alias Name"
                  },
                  {
                    type: 'textarea',
                    name: 'data',
                    label: 'PEM Data'
                  }
                ]
            }
        }
  },
  components: {
    ObjectList,
    CertificateEditor
  },
  methods: {
        getNewObject: function() {
            var obj ={ "_id":null };
            this.schema.fields.forEach((f)=> obj[f.name]=f.value );
            return obj;
        },
        getList: async () => await Utils.Crud.getList('cert').then((results) => {
          console.log(results);
          for(var i in results) {
              results[i]={ 
                  key: results[i], 
                  data: { "#": i,  "Certificate ID": results[i] }, 
                  actions: { 
                      "Edit":  { name: 'CertificateEditor', params:{id: i }}  
                      }  
                }
          }
          return results;
        }),
        getObject: async function(index) {
            var newObj = this.getNewObject();
            var obj = await Utils.Crud.getObject("cert",index,newObj);
            return obj;
        },
        saveObject: async function(data) {
          console.log(data);
          try {
            console.log('Saving',data);
            var old_id=data._id;
            delete data._id;
            if(old_id !== null) {
                await Utils.Crud.deleteObject('cert',old_id).then( () => this.importCertificate(data));
            }else{
                await Utils.Crud.importCertificate(data);
            }
            this.$router.push({name: 'Certificates'});
          }catch(e) {
              alert(e);
          }
          
        },
        deleteObject: async function(index) {
            try {
                await Utils.Crud.deleteObject('cert',index);
                this.$router.push({name: 'Certificates'});
            }catch(e) {
                alert(e);
            }
        },
        revert: function() {
          this.$router.push({name: 'Certificates'});
        }
        
    }
}
</script>
