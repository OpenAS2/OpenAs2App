<template>
    <div id="partners">
        <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
        <h1 class="h2">Partners</h1>
        <div class="btn-toolbar mb-2 mb-md-0">
          <div class="btn-group mr-2">
            <button type="button"  @click="$router.push({ name: 'PartnerEditor',params: { id: 'new'} })" class="btn btn-sm btn-outline-secondary">New</button>
            <!-- <button type="button" class="btn btn-sm btn-outline-secondary">Export</button> -->
          </div>
        </div>
      </div>
        <object-editor v-if="$route.params.id" 
            title="Partner Editor"
            :schema="schema"
            :model="getObject($route.params.id)"
            @commit="saveObject" 
            @delete="(id)=>this.deleteObject(id).then(this.$router.push({name:'Partners'}))" 
            @revert="()=>this.$router.push({name: 'Partners'})">
            
        </object-editor>
        <div v-else>
            <object-list :src="getList"></object-list>
        </div>
    </div>
</template>
<script>
import ObjectList from './ObjectList';
import ObjectEditor from './ObjectEditor';
import Utils from '../utils';
export default {
    data: function() {
        return {
            schema: { 
                fields: [
                    { 
                        type: "text",
                        name: "name",
                        label: "Partner ID",
                        placeholder: "Trading Partner's Unique Key Name"
                    },
                    {
                        type: "text",
                        name: "as2_id",
                        label: "AS2 ID",
                        placeholder: "Trading Partner's AS2 ID"
                    },
                    {
                        type: "email",
                        name: "email",
                        label: "Email",
                        placeholder: "Trading Partner's Email Address"
                    },
                    {
                        type: "select",
                        name: "x509_alias",
                        label: "Certificate Alias",
                        placeholder: "X.509 Certificate Alias on the KeyStore",
                        list: []
                    }
                ]
            }
        };
    },
    components: {
        ObjectList,
        ObjectEditor
    },
    mounted: async function() {
        var list=await Utils.Crud.getList('cert');
        console.log(list);
        this.schema.fields[3].list=Object.create(list);
    },
    methods: {
        getNewObject: function() {
            var obj ={ "_id":null };
            this.schema.fields.forEach((f)=> obj[f.name]=f.value );
            return obj;
        },
        getList: async () => await Utils.Crud.getList('partner').then((results) => {
          for(var i in results) {
              results[i]={ 
                  key: results[i], 
                  data: { "#": i,  "Partner ID": results[i] }, 
                  actions: { 
                      "Edit":  { name: 'PartnerEditor', params:{id: i }}  
                      }  
                }
          }
          return results;
        }),
        getObject: async function(index) {
            var newObj = this.getNewObject();
            return await Utils.Crud.getObject("partner",index,newObj)
        },
        saveObject: async function(data) {
          console.log(data);
          try {
            await Utils.Crud.saveObject('partner',data);
            this.$router.push({name: 'Partners'});
          }catch(e) {
              alert(e);
          }
          
        },
        deleteObject: async function(index) {
            try {
                await Utils.Crud.deleteObject('partner',index);
                this.$router.push({name: 'Partners'});
            }catch(e) {
                alert(e);
            }
        },
        revert: function() {
            this.$router.push({name: 'Partners'});
        }
    }

}
</script>
<style >

</style>